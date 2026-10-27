package edu.unsw.service;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.*;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.AssessmentPlanSaveDto;
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

/** Owner scoping, real SQL persistence, live booking validation, and deterministic planning arithmetic. */
class AssessmentPlanningServiceTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-22T23:00:00Z"),ZoneId.of("Australia/Sydney"));
    static final LocalDateTime NOW=LocalDateTime.now(CLOCK);
    JdbcTemplate jdbc;
    AssessmentPlanMapper plans;
    AssessmentBookingMapper bookings;
    AssessmentSlotMapper slots;
    WbaTypeMapper types;
    UserMapper users;
    AssessmentPlanningService service;
    BookingSubmissionService submissions;
    StudentSlotSearchService search;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(source);
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(var mapper:List.of(AssessmentPlanMapper.class,AssessmentBookingMapper.class,AssessmentSlotMapper.class,WbaTypeMapper.class,UserMapper.class,MessageMapper.class)) config.addMapper(mapper);
        var template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        plans=template.getMapper(AssessmentPlanMapper.class); bookings=template.getMapper(AssessmentBookingMapper.class);
        slots=template.getMapper(AssessmentSlotMapper.class); types=template.getMapper(WbaTypeMapper.class); users=template.getMapper(UserMapper.class);
        for(var type:List.of(AssessmentPlan.class,AssessmentBooking.class,AssessmentSlot.class,WbaType.class,User.class,Message.class)) createTable(type);
        var submissionTarget=new BookingSubmissionService(bookings,slots,users,template.getMapper(MessageMapper.class),mock(RedissonUtils.class),CLOCK);
        var submissionProxy=new ProxyFactory(submissionTarget); submissionProxy.setProxyTargetClass(true);
        submissionProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        submissions=(BookingSubmissionService)submissionProxy.getProxy();
        search=new StudentSlotSearchService(slots,bookings,users,CLOCK);
        var target=new AssessmentPlanningService(plans,types,bookings,slots,users,search,submissions,CLOCK);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        service=(AssessmentPlanningService)proxy.getProxy();
        user(1,4); user(2,4); user(10,3); user(20,3); actor(1,"Stu");
        type(100,"Mini-CEX"); type(200,"DOP"); slot(1000,10,"Mini-CEX",NOW.plusDays(4));
    }
    @AfterEach void cleanup() { CurrentUserUtils.remove(); jdbc.execute("DROP ALL OBJECTS"); }

    @Test void noPlanDoesNotInventCourseTargetsAndOnlyListsEnabledTypes() {
        jdbc.update("UPDATE wba_type SET is_enable=FALSE WHERE id=200");
        var result=service.get(); assertFalse(result.isConfigured()); assertEquals(0,result.getTotalTarget()); assertTrue(result.getGoals().isEmpty());
        assertEquals(List.of("Mini-CEX"),result.getAvailableTypes().stream().map(t->t.name()).toList());
        assertEquals("Australia/Sydney",result.getTimeZone()); assertEquals(0,count("assessment_booking"));
    }

    @Test void savesPersonalTargetsAndReplaysIdenticalSaveWithoutExtraVersions() {
        var request=plan(3); assertEquals(1,service.save(request)); assertEquals(1,service.save(request));
        var view=service.get(); assertEquals(3,view.getTotalTarget()); assertEquals(3,view.getRemaining()); assertEquals(3,view.getToRequest());
        assertEquals(1,count("student_assessment_plan")); assertEquals(0,count("assessment_booking")); assertEquals(0,count("message"));
        assertFalse(service.get().getGoals().getFirst().getCandidates().isEmpty());
        actor(2,"Stu"); assertFalse(service.get().isConfigured());
    }

    @Test void rolesAndDisabledStudentsCannotReadOrChangePlans() {
        for(String role:List.of("PS","Admin","SuAdmin")) {
            actor(1,role); assertThrows(BusinessException.class,()->service.get()); assertThrows(BusinessException.class,()->service.save(plan(2)));
        }
        actor(1,"Stu"); jdbc.update("UPDATE `user` SET is_enable=FALSE WHERE id=1");
        assertThrows(BusinessException.class,()->service.summary()); assertThrows(BusinessException.class,()->service.save(plan(2)));
        assertEquals(0,count("student_assessment_plan"));
    }

    @Test void progressUsesCompletionDateAndActualOccurrenceAndSeparatesAllStatuses() {
        var request=plan(8); request.setStartDate(NOW.toLocalDate().minusDays(3)); service.save(request);
        booking(1,1,1000,2,NOW.minusDays(10),NOW.minusDays(1)); // completion inside period, template/start outside
        booking(2,1,1000,2,NOW,NOW.minusDays(8)); // completion outside period
        booking(3,1,1000,1,NOW.plusDays(4),null);
        booking(4,1,1000,0,NOW.plusDays(5),null);
        booking(5,1,1000,3,NOW.plusDays(6),null); booking(6,1,1000,4,NOW.plusDays(7),null);
        booking(7,1,1000,0,NOW.minusDays(1),null);
        booking(8,2,1000,2,NOW,NOW.minusDays(1)); // other student
        booking(9,1,1000,2,NOW,NOW.minusDays(1)); jdbc.update("UPDATE assessment_booking SET is_delete=TRUE WHERE id=9");
        booking(10,1,1000,2,NOW,NOW.plusDays(1)); // invalid future completion cannot count
        var row=service.summary().getGoals().getFirst();
        assertEquals(1,row.getCompleted()); assertEquals(1,row.getConfirmed()); assertEquals(1,row.getPending()); assertEquals(1,row.getMissed());
        assertEquals(7,row.getRemaining()); assertEquals(5,row.getToRequest());
    }

    @Test void deletedTemplateRetainsCompletedProgressButMissingCompletionDateIsUncounted() {
        var request=plan(2); request.setStartDate(NOW.toLocalDate().minusDays(2)); service.save(request);
        booking(1,1,1000,2,NOW.minusDays(1),NOW.minusHours(1)); booking(2,1,1000,2,NOW.minusDays(1),null);
        jdbc.update("UPDATE assessment_slot SET is_delete=TRUE WHERE id=1000");
        var result=service.get(); assertEquals(1,result.getCompleted()); assertEquals(1,result.getUncountedRecords()); assertTrue(result.getGoals().getFirst().getCandidates().isEmpty());
    }

    @Test void weeklyRequestTotalsAreConservedAndBookedCountsStayInTheirActualWeek() {
        var request=plan(8); request.setStartDate(NOW.toLocalDate().minusDays(4)); request.setDeadline(LocalDate.of(2026,11,5)); service.save(request);
        booking(1,1,1000,2,NOW.minusDays(1),NOW.minusDays(1)); booking(2,1,1000,2,NOW.minusDays(2),NOW.minusDays(1));
        booking(3,1,1000,1,LocalDateTime.of(2026,10,27,14,0),null); booking(4,1,1000,0,LocalDateTime.of(2026,10,24,14,0),null);
        var view=service.summary(); assertEquals(3,view.getWeeksRemaining()); assertEquals(4,view.getToRequest());
        assertEquals(4,view.getWeeks().stream().flatMap(w->w.getGoals().stream()).mapToInt(g->g.toRequest()).sum());
        assertEquals(1,view.getWeeks().getFirst().getGoals().getFirst().pending()); assertEquals(1,view.getWeeks().get(1).getGoals().getFirst().confirmed());
        assertEquals(2,view.getGoals().getFirst().getSuggestedPerWeek());
    }

    @Test void followUpsAreOwnedPeriodAndTypeScopedAndOrderedByActionThenActualOccurrence() {
        var request=plan(10); request.setStartDate(NOW.toLocalDate().minusDays(3)); service.save(request);
        booking(1,1,1000,0,NOW.minusDays(1),null);
        booking(2,1,1000,1,NOW.minusHours(1),null);
        booking(3,1,1000,1,NOW.minusMinutes(10),null);
        booking(4,1,1000,1,NOW.plusHours(48),null);
        booking(5,1,1000,0,NOW.plusDays(1),null);
        booking(6,1,1000,1,NOW.plusHours(48).plusSeconds(1),null);
        booking(7,2,1000,0,NOW.plusHours(2),null); // other student
        booking(8,1,1000,4,NOW.minusHours(2),null); // canceled
        booking(9,1,1000,3,NOW.minusHours(2),null); // declined
        booking(10,1,1000,2,NOW.minusHours(2),NOW.minusHours(1));
        booking(11,1,1000,0,NOW.plusDays(29),null); // beyond the inclusive deadline
        booking(12,1,1000,0,NOW.minusDays(4),null); // before start
        slot(2000,20,"DOP",NOW.plusDays(1)); booking(13,1,2000,0,NOW.plusHours(3),null); // not a target type
        booking(14,1,1000,0,NOW.plusHours(4),null); jdbc.update("UPDATE assessment_booking SET is_delete=TRUE WHERE id=14");
        var result=service.summary();
        assertEquals(List.of("1","2","3","4","5","6"),result.getFollowUps().stream().map(i->i.bookingId()).toList());
        assertEquals(List.of("UNAPPROVED_SESSION","AWAITING_COMPLETION","IN_PROGRESS","UPCOMING","PENDING_APPROVAL","SCHEDULED"),result.getFollowUps().stream().map(i->i.kind()).toList());
        assertEquals(NOW.minusDays(1),result.getFollowUps().getFirst().startTime()); // not slot template's date
        assertEquals("Demo 10",result.getFollowUps().getFirst().supervisorName());
        assertEquals(6,result.getFollowUpTotal()); assertFalse(result.isHasMoreFollowUps());
        assertEquals(2,result.getFollowUpCounts().needsAttention()); assertEquals(1,result.getFollowUpCounts().awaitingApproval());
        assertEquals(1,result.getFollowUpCounts().inProgress()); assertEquals(1,result.getFollowUpCounts().upcoming()); assertEquals(1,result.getFollowUpCounts().scheduled());
        assertEquals(NOW,result.getGeneratedAt()); assertEquals(28,result.getDaysToDeadline());
        assertEquals(0,count("message"));
    }

    @Test void ongoingConfirmedSessionCoversGapWithoutBecomingMissedOrCompletedAndPendingDoesNot() {
        service.save(plan(2));
        booking(1,1,1000,1,NOW,null); // at the inclusive start
        booking(2,1,1000,0,NOW,null); // time passed without approval
        var result=service.get(); var goal=result.getGoals().getFirst();
        assertEquals(1,result.getInProgress()); assertEquals(0,result.getCompleted()); assertEquals(0,goal.getMissed());
        assertEquals(0,result.getConfirmed()); assertEquals(0,result.getPending()); assertEquals(1,result.getToRequest());
        assertEquals(1,result.getFollowUpCounts().needsAttention());
        jdbc.update("UPDATE assessment_booking SET start_time=?,end_time=? WHERE id=1",NOW.minusMinutes(30),NOW);
        result=service.summary(); assertEquals(0,result.getInProgress()); assertEquals(0,result.getCompleted());
        assertEquals(1,result.getGoals().getFirst().getMissed()); assertEquals(2,result.getToRequest());
    }

    @Test void followUpsRetainArchivedTemplatesButSkipMissingOrInvalidDatesAndMissingTemplates() {
        service.save(plan(2));
        booking(1,1,1000,0,NOW.plusDays(1),null);
        booking(2,1,1000,1,NOW.plusDays(1),null); jdbc.update("UPDATE assessment_booking SET end_time=start_time WHERE id=2");
        booking(3,1,1000,0,NOW.plusDays(2),null); jdbc.update("UPDATE assessment_booking SET start_time=NULL WHERE id=3");
        booking(4,1,9999,0,NOW.plusDays(3),null);
        jdbc.update("UPDATE assessment_slot SET is_delete=TRUE WHERE id=1000");
        jdbc.update("UPDATE `user` SET is_delete=TRUE WHERE id=10");
        var result=service.summary(); assertEquals(1,result.getFollowUpTotal()); assertEquals(3,result.getUncountedRecords());
        assertEquals(0,result.getWeeks().stream().flatMap(w->w.getGoals().stream()).mapToInt(g->g.confirmed()).sum());
        assertEquals(1,result.getWeeks().stream().flatMap(w->w.getGoals().stream()).mapToInt(g->g.pending()).sum());
        assertEquals("Supervisor unavailable",result.getFollowUps().getFirst().supervisorName());
        assertEquals("1",result.getFollowUps().getFirst().bookingId());
    }

    @Test void followUpCapKeepsFullCountsAndReReadingReflectsStatusChangesWithoutWriting() {
        service.save(plan(1));
        for(int id=1;id<=24;id++) booking(id,1,1000,0,NOW.plusHours(id),null);
        var result=service.summary(); assertEquals(24,result.getFollowUpTotal()); assertTrue(result.isHasMoreFollowUps());
        assertEquals(20,result.getFollowUps().size()); assertEquals(24,result.getFollowUpCounts().awaitingApproval());
        jdbc.update("UPDATE assessment_booking SET status=1 WHERE id=24");
        result=service.summary(); assertEquals("24",result.getFollowUps().getFirst().bookingId()); assertEquals("UPCOMING",result.getFollowUps().getFirst().kind());
        assertEquals(23,result.getFollowUpCounts().awaitingApproval()); assertEquals(1,result.getFollowUpCounts().upcoming());
        jdbc.update("UPDATE assessment_booking SET status=4 WHERE id=24");
        assertEquals(23,service.summary().getFollowUpTotal()); assertEquals(24,count("assessment_booking")); assertEquals(0,count("message"));
    }

    @Test void overdueOrCompletedPlanStillShowsUnresolvedRecordsWithoutInventingCompletion() {
        var request=plan(1); request.setStartDate(NOW.toLocalDate().minusDays(4)); request.setDeadline(NOW.toLocalDate().minusDays(1)); service.save(request);
        booking(1,1,1000,1,NOW.minusDays(1),null);
        var result=service.summary(); assertEquals(-1,result.getDaysToDeadline()); assertEquals("OVERDUE",result.getStatus());
        assertEquals("AWAITING_COMPLETION",result.getFollowUps().getFirst().kind());
        booking(2,1,1000,2,NOW.minusDays(2),NOW.minusDays(1));
        result=service.summary(); assertEquals("COMPLETE",result.getStatus()); assertEquals(1,result.getFollowUpTotal()); assertEquals(1,result.getCompleted());
        actor(2,"Stu"); assertEquals(0,service.summary().getFollowUpTotal()); assertNull(service.summary().getDaysToDeadline());
    }

    @Test void upcomingMeans48ElapsedHoursAcrossSydneySpringDaylightSavingChange() {
        var springClock=Clock.fixed(Instant.parse("2026-10-02T23:00:00Z"),ZoneId.of("Australia/Sydney"));
        var springService=new AssessmentPlanningService(plans,types,bookings,slots,users,search,submissions,springClock);
        var request=plan(2); request.setStartDate(LocalDate.of(2026,10,3)); request.setDeadline(LocalDate.of(2026,10,6)); springService.save(request);
        booking(1,1,1000,1,LocalDateTime.of(2026,10,5,9,30),null); // 47.5 actual hours, 48.5 wall hours
        booking(2,1,1000,1,LocalDateTime.of(2026,10,5,10,0),null); // exactly 48 actual hours
        booking(3,1,1000,1,LocalDateTime.of(2026,10,5,10,0,1),null);
        var result=springService.summary(); assertEquals(2,result.getFollowUpCounts().upcoming()); assertEquals(1,result.getFollowUpCounts().scheduled());
        assertEquals(List.of("UPCOMING","UPCOMING","SCHEDULED"),result.getFollowUps().stream().map(i->i.kind()).toList());
    }

    @Test void myBookingsCanOpenOwnedFollowUpAfterTemplateOrSupervisorArchivalWithoutExposingAnotherStudent() {
        booking(1,1,1000,0,NOW.plusDays(1),null); booking(2,2,1000,0,NOW.plusDays(2),null);
        jdbc.update("UPDATE assessment_slot SET is_delete=TRUE WHERE id=1000");
        jdbc.update("UPDATE `user` SET is_delete=TRUE WHERE id=10");
        jdbc.update("UPDATE assessment_booking SET end_time=NULL WHERE id=1");
        var reader=new edu.unsw.service.impl.StudentBookingServiceImpl(slots,users,mock(BookingManagementService.class),submissions,search);
        org.springframework.test.util.ReflectionTestUtils.setField(reader,"baseMapper",bookings);
        var query=new edu.unsw.domain.dto.BaseQueryDto(); query.setPageNum(1); query.setPageQuantity(-1);
        var records=reader.getMyBookings(query).getRecords(); assertEquals(1,records.size());
        assertEquals(1L,records.getFirst().getBookingId()); assertEquals("Supervisor unavailable",records.getFirst().getSupervisorName());
        assertEquals("Unknown",records.getFirst().getDurationMinutes()); assertEquals(NOW.plusDays(1),records.getFirst().getStartTime());
    }

    @Test void deadlineIncludesItsWholeDayAndOverduePlansOfferNoFutureBookings() {
        var request=plan(2); request.setStartDate(NOW.toLocalDate().minusDays(2)); request.setDeadline(NOW.toLocalDate().minusDays(1)); service.save(request);
        booking(1,1,1000,2,NOW.minusDays(1),NOW.toLocalDate().minusDays(1).atTime(23,59));
        var result=service.get(); assertEquals(1,result.getCompleted()); assertEquals("OVERDUE",result.getStatus()); assertTrue(result.getWeeks().isEmpty());
        assertTrue(result.getGoals().getFirst().getCandidates().isEmpty()); assertEquals(0,result.getGoals().getFirst().getSuggestedPerWeek());
    }

    @Test void completedTargetsNeverProduceNegativeGapsOrExtraRecommendations() {
        var request=plan(1); request.setStartDate(NOW.toLocalDate().minusDays(2)); service.save(request);
        booking(1,1,1000,2,NOW.minusDays(1),NOW.minusHours(2)); booking(2,1,1000,2,NOW.minusDays(1),NOW.minusHours(1));
        var result=service.get(); assertEquals("COMPLETE",result.getStatus()); assertEquals(1,result.getCompleted());
        assertEquals(2,result.getGoals().getFirst().getCompleted()); assertEquals(0,result.getRemaining()); assertEquals(0,result.getToRequest()); assertTrue(result.getGoals().getFirst().getCandidates().isEmpty());
    }

    @Test void recommendationsRespectTypeDeadlineOccupancyAndStudentOrSupervisorOverlap() {
        var request=plan(2); var dop=goal(200,2); request.setGoals(List.of(dop)); service.save(request);
        slot(1001,20,"DOP",NOW.plusDays(4)); // overlaps student booking with another supervisor
        booking(1,1,1000,1,NOW.plusDays(4),null);
        slot(1002,10,"DOP",NOW.plusDays(5)); slot(1003,10,"Mini-CEX",NOW.plusDays(5));
        booking(2,2,1003,0,NOW.plusDays(5),null); // supervisor busy across templates
        slot(1004,20,"DOP",NOW.plusDays(6)); // eligible
        slot(1005,20,"DOP",NOW.plusDays(40)); // outside 30-day lookahead
        slot(1006,20,"DOP",NOW.plusDays(7)); jdbc.update("UPDATE assessment_slot SET is_enable=FALSE WHERE id=1006");
        var result=service.get(); assertEquals(List.of("1004"),result.getGoals().getFirst().getCandidates().stream().map(c->c.getSlotId()).toList());
        assertEquals(2,count("assessment_booking")); assertEquals(0,count("message"));
        assertFalse(result.getRecommendationEnd().isAfter(request.getDeadline().plusDays(1).atStartOfDay()));
    }

    @Test void renamedTypeDoesNotSilentlyChangeSavedGoalsOrRecommendAnotherAssessment() {
        service.save(plan(2)); jdbc.update("UPDATE wba_type SET name='Renamed type' WHERE id=100");
        var row=service.get().getGoals().getFirst(); assertEquals("Mini-CEX",row.getType()); assertFalse(row.isTypeAvailable()); assertTrue(row.getCandidates().isEmpty());
    }

    @Test void validationRejectsDuplicateUnknownDisabledTargetsAndInvalidDateSpans() {
        var duplicate=plan(2); duplicate.setGoals(List.of(goal(100,2),goal(100,3)));
        var unknown=plan(2); unknown.setGoals(List.of(goal(999,2)));
        var negative=plan(2); negative.setGoals(List.of(goal(100,0)));
        var reverse=plan(2); reverse.setDeadline(reverse.getStartDate().minusDays(1));
        var tooLong=plan(2); tooLong.setDeadline(tooLong.getStartDate().plusDays(366));
        for(var invalid:List.of(duplicate,unknown,negative,reverse,tooLong)) assertThrows(BusinessException.class,()->service.save(invalid));
        jdbc.update("UPDATE wba_type SET is_enable=FALSE WHERE id=100"); assertThrows(BusinessException.class,()->service.save(plan(2)));
        assertEquals(0,count("student_assessment_plan"));
    }

    @Test void concurrentDifferentEditsHaveOneWinnerAndStaleTabCannotOverwriteIt() throws Exception {
        service.save(plan(2));
        var first=plan(3); first.setExpectedVersion(1); var second=plan(4); second.setExpectedVersion(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return attempt(first);}); var b=pool.submit(()->{gate.await();return attempt(second);}); gate.countDown();
            var values=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,values.stream().filter(Integer.class::isInstance).count()); assertEquals(1,values.stream().filter(BusinessException.class::isInstance).count());
        }
        assertEquals(2,plans.selectById(1L).getVersion());
        var stale=plan(9); stale.setExpectedVersion(1); assertThrows(BusinessException.class,()->service.save(stale));
    }

    @Test void actualSearchReturnsOnlyRealSingleConditionAlternativesAndNeverBooks() {
        var afternoon=NOW.plusDays(4).withHour(14);
        slot(1001,20,"Mini-CEX",afternoon);slot(1002,10,"Mini-CEX",afternoon.plusWeeks(1));
        var result=alternativeAssistant().search(assistantRequest());
        assertEquals(edu.unsw.domain.vo.BookingAssistantResult.Status.NO_RESULTS,result.getStatus());assertTrue(result.getCandidates().isEmpty());
        assertEquals(List.of("DATE","TIME","SUPERVISOR"),result.getAlternatives().stream().map(a->a.getKind()).toList());
        assertEquals(List.of("1002","1000","1001"),result.getAlternatives().stream().flatMap(a->a.getCandidates().stream()).map(c->c.getSlotId()).toList());
        assertEquals(10L,result.getConditions().getSupervisorId());assertEquals(12,result.getConditions().getStartTime().getHour());assertEquals(0,count("assessment_booking"));
    }
    @Test void aRealSupervisorConflictAcrossTemplatesRemovesAnAlternativeWithoutFailingTheSearch() {
        var afternoon=NOW.plusDays(4).withHour(14);
        slot(1001,20,"Mini-CEX",afternoon);slot(1002,10,"Mini-CEX",afternoon.plusWeeks(1));slot(1003,10,"DOP",afternoon.plusWeeks(1));
        booking(1,2,1003,0,afternoon.plusWeeks(1),null);
        var result=alternativeAssistant().search(assistantRequest());
        assertEquals(edu.unsw.domain.vo.BookingAssistantResult.Status.NO_RESULTS,result.getStatus());
        assertEquals(List.of("TIME","SUPERVISOR"),result.getAlternatives().stream().map(a->a.getKind()).toList());
        assertEquals(1,count("assessment_booking"));assertEquals(0,count("message"));
    }
    private edu.unsw.service.assistant.BookingAssistantService alternativeAssistant() {
        var parser=mock(edu.unsw.service.assistant.BookingIntentParser.class);org.mockito.Mockito.when(parser.available()).thenReturn(true);
        org.mockito.Mockito.when(parser.parse(org.mockito.ArgumentMatchers.any())).thenReturn(new edu.unsw.service.assistant.BookingIntent(
                edu.unsw.service.assistant.BookingIntent.Action.SEARCH,null,null,"Mini-CEX","Demo 10",edu.unsw.service.assistant.BookingIntent.SupervisorMode.REQUIRED,
                NOW.plusDays(4).withHour(12),NOW.plusDays(4).withHour(18),LocalTime.NOON,LocalTime.of(18,0),List.of(2),null));
        var dictionary=mock(DictionaryService.class);org.mockito.Mockito.when(dictionary.getEnabledHospitals()).thenReturn(List.of());org.mockito.Mockito.when(dictionary.getEnabledDepartments()).thenReturn(List.of());
        org.mockito.Mockito.when(dictionary.getEnabledWbaTypes()).thenReturn(types.selectList(null));
        return new edu.unsw.service.assistant.BookingAssistantService(parser,dictionary,users,search,CLOCK,mock(BookingManagementService.class),submissions,service);
    }
    private edu.unsw.domain.dto.BookingAssistantRequest assistantRequest() {var q=new edu.unsw.domain.dto.BookingAssistantRequest();q.setMessage("Find afternoon Mini-CEX with Demo 10");return q;}

    private Object attempt(AssessmentPlanSaveDto request) { actor(1,"Stu"); try{return service.save(request);}catch(BusinessException e){return e;}finally{CurrentUserUtils.remove();} }
    private AssessmentPlanSaveDto plan(int target) {
        var request=new AssessmentPlanSaveDto(); request.setExpectedVersion(0); request.setStartDate(NOW.toLocalDate()); request.setDeadline(NOW.toLocalDate().plusDays(28)); request.setGoals(List.of(goal(100,target))); return request;
    }
    private AssessmentPlanSaveDto.Goal goal(long type,int target) { var result=new AssessmentPlanSaveDto.Goal(); result.setTypeId(type); result.setTarget(target); return result; }
    private void type(long id,String name) { var t=new WbaType(); t.setId(id); t.setName(name); t.setIsEnable(true); t.setIsDelete(false); types.insert(t); }
    private static void actor(long id,String role) { var user=new CurrentUser(); user.setId(id); user.setRoleKey(role); CurrentUserUtils.setUser(user); }
    private void user(long id,long role) { var u=new User(); u.setId(id); u.setRoleId(role); u.setName("Demo "+id); u.setIsEnable(true); u.setIsCanceled(false); u.setIsDelete(false); users.insert(u); }
    private void slot(long id,long mentor,String type,LocalDateTime start) {
        var s=new AssessmentSlot(); s.setId(id); s.setCreator(mentor); s.setType(type); s.setStartTime(start); s.setEndTime(start.plusMinutes(30)); s.setStatus(0); s.setRepeatType(0); s.setVersion(0); s.setIsEnable(true); s.setIsDelete(false); slots.insert(s);
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
