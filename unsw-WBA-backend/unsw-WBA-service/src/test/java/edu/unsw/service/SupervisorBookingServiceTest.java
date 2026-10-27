package edu.unsw.service;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.*;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.AssessmentPlanSaveDto;
import edu.unsw.domain.dto.AssessmentCompletionDto;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import static edu.unsw.service.SupervisorBookingService.Action.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import edu.unsw.domain.entity.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.*;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SupervisorBookingServiceTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-22T23:00:00Z"),ZoneId.of("Australia/Sydney"));
    static final LocalDateTime NOW=LocalDateTime.now(CLOCK);
    JdbcTemplate jdbc; DataSourceTransactionManager transactions;
    AssessmentBookingMapper bookings; AssessmentSlotMapper slots; UserMapper users; WbaTypeMapper types; AssessmentPlanMapper plans; MessageMapper messages;
    RedissonUtils redis; SupervisorBookingService service; AssessmentPlanningService planning;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(source);transactions=new DataSourceTransactionManager(source);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(var mapper:List.of(AssessmentPlanMapper.class,AssessmentBookingMapper.class,AssessmentSlotMapper.class,WbaTypeMapper.class,UserMapper.class,MessageMapper.class)) config.addMapper(mapper);
        var template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        plans=template.getMapper(AssessmentPlanMapper.class);bookings=template.getMapper(AssessmentBookingMapper.class);slots=template.getMapper(AssessmentSlotMapper.class);
        users=template.getMapper(UserMapper.class);types=template.getMapper(WbaTypeMapper.class);messages=template.getMapper(MessageMapper.class);
        for(var type:List.of(AssessmentPlan.class,AssessmentBooking.class,AssessmentSlot.class,WbaType.class,User.class,Message.class)) createTable(type);
        jdbc.execute("ALTER TABLE assessment_slot ALTER COLUMN next_available_time SET NOT NULL");
        redis=mock(RedissonUtils.class);service=service(CLOCK,messages,redis);
        var search=new StudentSlotSearchService(slots,bookings,users,CLOCK);
        planning=new AssessmentPlanningService(plans,types,bookings,slots,users,search,mock(BookingSubmissionService.class),CLOCK);
        user(1,4);user(2,4);user(10,3);user(20,3);type(100,"Mini-CEX");
        slot(1000,10,"Mini-CEX",NOW.plusDays(4));jdbc.update("UPDATE assessment_slot SET status=1,version=17 WHERE id=1000");
        actor(1,"Stu");planning.save(plan(2));actor(10,"PS");
    }
    @AfterEach void cleanup() {CurrentUserUtils.remove();jdbc.execute("DROP ALL OBJECTS");}
    private SupervisorBookingService service(Clock clock,MessageMapper mapper,RedissonUtils publisher) {
        var proxy=new ProxyFactory(new SupervisorBookingService(bookings,slots,users,mapper,publisher,clock));proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (SupervisorBookingService)proxy.getProxy();
    }
    private AssessmentCompletionDto result(int version,double score,String feedback) {
        var request=new AssessmentCompletionDto();request.setBookingId(1L);request.setVersion(version);request.setScore(score);request.setFeedback(feedback);return request;
    }
    private edu.unsw.domain.vo.AssessmentPlanVo progress() {actor(1,"Stu");try{return planning.summary();}finally{actor(10,"PS");}}
    private edu.unsw.domain.vo.UpcomingSessionVo session(int status) {var q=new AssessmentSlotQueryDto();q.setPageQuantity(-1);return service.sessions(q,status).getRecords().getFirst();}

    @Test void ownerRoleAndActiveAccountAreRequiredForEveryDecision() {
        booking(1,1,1000,0,NOW.plusDays(4),null);
        for(String role:List.of("Stu","Admin","SuAdmin")) {actor(10,role);assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));assertThrows(BusinessException.class,()->session(0));}
        actor(20,"PS");for(var action:List.of(APPROVE,DECLINE,CANCEL)) assertThrows(BusinessException.class,()->service.decide(action,1000L,1L,0,true));
        assertThrows(BusinessException.class,()->service.complete(result(0,80,"Reviewed")));assertThrows(BusinessException.class,()->service.correct(result(0,80,"Reviewed")));
        actor(10,"PS");jdbc.update("UPDATE `user` SET is_enable=FALSE WHERE id=10");assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));
        assertEquals(0,bookings.selectById(1).getStatus());assertEquals(0,count("message"));
    }
    @Test void suppliedSlotMustMatchTheOwnedBooking() {
        booking(1,1,1000,0,NOW.plusDays(4),null);slot(2000,20,"Mini-CEX",NOW.plusDays(4));
        assertThrows(BusinessException.class,()->service.decide(DECLINE,2000L,1L,0,true));assertEquals(0,bookings.selectById(1).getStatus());assertEquals(0,slots.selectById(2000).getStatus());
    }
    @Test void listingUsesActualOccurrenceBookingVersionAndScopedCapabilitiesIncludingArchivedRecords() {
        booking(1,1,1000,0,NOW.plusDays(5),null);booking(2,2,1000,1,NOW.minusHours(1),null);
        slot(2000,20,"Mini-CEX",NOW.plusDays(1));booking(3,1,2000,0,NOW.plusDays(1),null);
        var pending=session(0);assertEquals(NOW.plusDays(5),pending.getStartTime());assertEquals(NOW.plusDays(5).plusMinutes(30),pending.getEndTime());
        assertEquals(0,pending.getBookingVersion());assertEquals(17,pending.getVersion());assertTrue(pending.isCanApprove());assertEquals(0,pending.getBookingStatus());
        jdbc.update("UPDATE assessment_slot SET is_delete=TRUE,status=2 WHERE id=1000");
        assertFalse(session(0).isCanApprove());assertTrue(session(0).isCanDecline());assertTrue(session(1).isCanComplete());assertFalse(session(1).isCanCancel());
    }
    @Test void approvalAndIdenticalRetrySaveOneDecisionTwoNoticesAndNoCompletion() {
        booking(1,1,1000,0,NOW.plusDays(4),null);
        assertEquals(1,progress().getPending());
        var first=service.decide(APPROVE,1000L,1L,0,null);assertEquals(1,first.getStatus());assertEquals(1,first.getVersion());assertFalse(first.isReplayed());
        assertTrue(service.decide(APPROVE,1000L,1L,0,null).isReplayed());assertEquals(2,count("message"));assertEquals(1,slots.selectById(1000).getStatus());
        assertEquals(0,progress().getCompleted());assertEquals(1,progress().getConfirmed());assertEquals(0,progress().getPending());
    }
    @Test void lateApprovalAndStaleOrMissingVersionsAreRejectedWithoutChangingRequests() {
        booking(1,1,1000,0,NOW,null);
        assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));
        assertThrows(BusinessException.class,()->service.decide(DECLINE,1000L,1L,17,true));
        assertThrows(BusinessException.class,()->service.decide(DECLINE,1000L,1L,null,true));assertEquals(0,count("message"));
        assertEquals(3,service.decide(DECLINE,1000L,1L,0,true).getStatus());
    }
    @Test void approvalChecksStudentAndSupervisorConflictsAcrossTemplatesAndInactiveStudent() {
        booking(1,1,1000,0,NOW.plusDays(4),null);slot(2000,20,"Mini-CEX",NOW.plusDays(4));slot(3000,10,"Mini-CEX",NOW.plusDays(4));
        booking(2,1,2000,1,NOW.plusDays(4),null);assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));
        jdbc.update("DELETE FROM assessment_booking WHERE id=2");booking(3,2,3000,0,NOW.plusDays(4),null);assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));
        jdbc.update("DELETE FROM assessment_booking WHERE id=3");jdbc.update("UPDATE assessment_slot SET status=2 WHERE id=1000");
        assertFalse(session(0).isCanApprove());assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));
        jdbc.update("UPDATE assessment_slot SET status=1 WHERE id=1000");jdbc.update("UPDATE `user` SET is_enable=FALSE WHERE id=1");assertThrows(BusinessException.class,()->service.decide(APPROVE,1000L,1L,0,null));assertEquals(0,count("message"));
    }
    @Test void declineReopensOnlyTheMatchingOneOffAndDifferentRetrySettingIsRejected() {
        booking(1,1,1000,0,NOW.plusDays(4),null);var response=service.decide(DECLINE,1000L,1L,0,true);
        assertEquals(3,response.getStatus());assertFalse(bookings.selectById(1).getIsDelete());assertEquals(0,slots.selectById(1000).getStatus());assertTrue(slots.selectById(1000).getIsEnable());
        assertTrue(service.decide(DECLINE,1000L,1L,0,true).isReplayed());assertThrows(BusinessException.class,()->service.decide(DECLINE,1000L,1L,0,false));assertEquals(2,count("message"));
    }
    @Test void supervisorCancellationCanKeepFutureSlotClosedAndNeverDeletesTheRecord() {
        booking(1,1,1000,1,NOW.plusHours(2),null);assertTrue(session(1).isCanCancel());
        assertEquals(4,service.decide(CANCEL,1000L,1L,0,false).getStatus());assertFalse(slots.selectById(1000).getIsEnable());assertFalse(bookings.selectById(1).getIsDelete());
        assertTrue(service.decide(CANCEL,1000L,1L,0,false).isReplayed());assertEquals(0,progress().getConfirmed());
    }
    @Test void recurringResolutionPreservesOtherOccurrencesAndDoesNotEnableDisabledSeries() {
        jdbc.update("UPDATE assessment_slot SET repeat_type=1,status=0,is_enable=FALSE WHERE id=1000");
        booking(1,1,1000,0,NOW.plusDays(4),null);booking(2,2,1000,1,NOW.plusDays(11),null);
        service.decide(DECLINE,1000L,1L,0,false);assertFalse(slots.selectById(1000).getIsEnable());assertEquals(1,bookings.selectById(2).getStatus());assertEquals(0,slots.selectById(1000).getStatus());
        booking(3,1,1000,1,NOW.minusHours(1),null);var request=result(0,80,"Observed assessment");request.setBookingId(3L);service.complete(request);
        assertEquals(1,bookings.selectById(2).getStatus());assertEquals(0,slots.selectById(1000).getStatus());assertFalse(slots.selectById(1000).getIsEnable());
    }
    @Test void onlyEndedConfirmedSessionsCanBeCompletedAndExactlyEndIsAllowed() {
        booking(1,1,1000,1,NOW,null);assertThrows(BusinessException.class,()->service.complete(result(0,80,"Observed")));
        for(int status:List.of(0,3,4)) {jdbc.update("UPDATE assessment_booking SET status=?,start_time=?,end_time=? WHERE id=1",status,NOW.minusMinutes(30),NOW);assertThrows(BusinessException.class,()->service.complete(result(0,80,"Observed")));}
        jdbc.update("UPDATE assessment_booking SET status=1 WHERE id=1");assertEquals(2,service.complete(result(0,80,"Observed")).getStatus());assertEquals(NOW,bookings.selectById(1).getCompletedTime());assertEquals(2,slots.selectById(1000).getStatus());
    }
    @Test void completionRetryDoesNotOverwriteTheDateOrResultAndDifferentRetryIsRejected() {
        booking(1,1,1000,1,NOW.minusHours(1),null);var request=result(0,80,"Observed");service.complete(request);
        var tomorrow=service(Clock.offset(CLOCK,Duration.ofDays(1)),messages,redis);
        assertTrue(tomorrow.complete(request).isReplayed());assertEquals(NOW,bookings.selectById(1).getCompletedTime());assertEquals(2,count("message"));
        assertThrows(BusinessException.class,()->tomorrow.complete(result(0,90,"Changed")));assertThrows(BusinessException.class,()->tomorrow.complete(result(1,90,"Changed")));
        assertEquals(1,progress().getCompleted());
    }
    @Test void correctionPreservesOriginalDateAndPeriodProgressAndIsIdempotent() {
        booking(1,1,1000,2,NOW.minusDays(10),NOW.minusDays(5));jdbc.update("UPDATE assessment_booking SET score=70,feedback='Original' WHERE id=1");
        assertEquals(0,progress().getCompleted());var request=result(0,90,"Corrected feedback");service.correct(request);
        assertEquals(NOW.minusDays(5),bookings.selectById(1).getCompletedTime());assertEquals(0,progress().getCompleted());assertEquals(90,bookings.selectById(1).getScore());
        assertTrue(service.correct(request).isReplayed());assertEquals(2,count("message"));assertThrows(BusinessException.class,()->service.correct(result(0,95,"Another change")));
    }
    @Test void resultValidationRejectsNonFiniteOutOfRangeBlankOversizedAndMissingVersions() {
        booking(1,1,1000,1,NOW.minusHours(1),null);
        for(double score:List.of(-1d,100.01,Double.NaN,Double.POSITIVE_INFINITY)) assertThrows(BusinessException.class,()->service.complete(result(0,score,"Observed")));
        for(String feedback:List.of(" ","x".repeat(256))) assertThrows(BusinessException.class,()->service.complete(result(0,80,feedback)));
        var request=result(0,80,"Observed");request.setVersion(null);assertThrows(BusinessException.class,()->service.complete(request));assertEquals(0,count("message"));assertEquals(1,bookings.selectById(1).getStatus());
    }
    @Test void durableNotificationFailureRollsBackDecisionAndAvailability() {
        booking(1,1,1000,0,NOW.plusDays(4),null);var failing=mock(MessageMapper.class);when(failing.insert(any(Message.class))).thenReturn(0);
        assertThrows(BusinessException.class,()->service(CLOCK,failing,redis).decide(DECLINE,1000L,1L,0,true));assertEquals(0,bookings.selectById(1).getStatus());assertEquals(0,bookings.selectById(1).getVersion());assertEquals(1,slots.selectById(1000).getStatus());
    }
    @Test void liveNotificationFailureCannotTurnCommittedCompletionIntoFailure() {
        booking(1,1,1000,1,NOW.minusHours(1),null);when(redis.publish(anyString(),any(NotificationDto.class))).thenThrow(new IllegalStateException("offline"));
        var response=service.complete(result(0,80,"Observed"));assertEquals("LIVE_DELAYED",response.getNotificationStatus());assertEquals(2,bookings.selectById(1).getStatus());assertEquals(2,count("message"));
    }
    @Test void concurrentIdenticalApprovalsProduceOneChangeAndOnePairOfNotices() throws Exception {
        booking(1,1,1000,0,NOW.plusDays(4),null);
        try(var executor=Executors.newFixedThreadPool(3)) {
            var futures=new ArrayList<Future<edu.unsw.domain.vo.SupervisorBookingResult>>();
            for(int i=0;i<3;i++) futures.add(executor.submit(()->{actor(10,"PS");try{return service.decide(APPROVE,1000L,1L,0,null);}finally{CurrentUserUtils.remove();}}));
            int fresh=0;for(var future:futures) if(!future.get(20,TimeUnit.SECONDS).isReplayed()) fresh++;assertEquals(1,fresh);
        }
        assertEquals(1,bookings.selectById(1).getVersion());assertEquals(2,count("message"));
    }
    @Test void conflictingConcurrentDecisionsCannotOverwriteEachOther() throws Exception {
        booking(1,1,1000,0,NOW.plusDays(4),null);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->decision(APPROVE));var second=executor.submit(()->decision(DECLINE));
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,bookings.selectById(1).getVersion());assertEquals(2,count("message"));assertTrue(List.of(1,3).contains(bookings.selectById(1).getStatus()));
    }
    private boolean decision(SupervisorBookingService.Action action) {actor(10,"PS");try{service.decide(action,1000L,1L,0,true);return true;}catch(BusinessException expected){return false;}finally{CurrentUserUtils.remove();}}

    private AssessmentPlanSaveDto plan(int target) {
        var request=new AssessmentPlanSaveDto(); request.setExpectedVersion(0); request.setStartDate(NOW.toLocalDate()); request.setDeadline(NOW.toLocalDate().plusDays(28)); request.setGoals(List.of(goal(100,target))); return request;
    }
    private AssessmentPlanSaveDto.Goal goal(long type,int target) { var result=new AssessmentPlanSaveDto.Goal(); result.setTypeId(type); result.setTarget(target); return result; }
    private void type(long id,String name) { var t=new WbaType(); t.setId(id); t.setName(name); t.setIsEnable(true); t.setIsDelete(false); types.insert(t); }
    private static void actor(long id,String role) { var user=new CurrentUser(); user.setId(id); user.setRoleKey(role); CurrentUserUtils.setUser(user); }
    private void user(long id,long role) { var u=new User(); u.setId(id); u.setRoleId(role); u.setName("Demo "+id); u.setIsEnable(true); u.setIsCanceled(false); u.setIsDelete(false); users.insert(u); }
    private void slot(long id,long mentor,String type,LocalDateTime start) {
        var s=new AssessmentSlot(); s.setId(id); s.setCreator(mentor); s.setType(type); s.setStartTime(start); s.setEndTime(start.plusMinutes(30)); s.setNextAvailableTime(start); s.setStatus(0); s.setRepeatType(0); s.setVersion(0); s.setIsEnable(true); s.setIsDelete(false); slots.insert(s);
    }
    private void booking(long id,long owner,long slot,int status,LocalDateTime start,LocalDateTime completed) {
        var b=new AssessmentBooking(); b.setId(id); b.setCreator(owner); b.setAssessmentSlotId(slot); b.setStatus(status); b.setStartTime(start); b.setEndTime(start.plusMinutes(30)); b.setCompletedTime(completed); b.setIsDelete(false); b.setVersion(0); bookings.insert(b);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    private void createTable(Class<?> type) {
        var table=TableInfoHelper.getTableInfo(type); var columns=new ArrayList<String>(); columns.add(table.getKeyColumn()+" BIGINT PRIMARY KEY");
        for(var field:table.getFieldList()) {
            var value=field.getPropertyType();
            var sql=value==LocalDate.class?"DATE":value==LocalDateTime.class?"TIMESTAMP":value==LocalTime.class?"TIME":value==Long.class?"BIGINT":value==Integer.class?"INTEGER":value==Boolean.class?"BOOLEAN":value==Double.class?"DOUBLE":"VARCHAR(10000)";
            columns.add(field.getColumn()+" "+sql);
        }
        jdbc.execute("CREATE TABLE "+table.getTableName()+" ("+String.join(",",columns)+")");
    }
}
