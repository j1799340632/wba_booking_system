package edu.unsw.service;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.*;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.vo.AssistantInsightsVo;
import org.springframework.transaction.support.TransactionTemplate;
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

class AssistantInsightsServiceTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-25T14:30:00Z"),ZoneId.of("Australia/Sydney"));
    static final LocalDateTime NOW=LocalDateTime.now(CLOCK);
    JdbcTemplate jdbc;UserMapper users;AssistantEventMapper events;AssistantTelemetryService telemetry;AssistantInsightsService service;
    DataSourceTransactionManager transactions;long next=100;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(source);transactions=new DataSourceTransactionManager(source);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        config.addMapper(UserMapper.class);config.addMapper(AssistantEventMapper.class);
        var template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));users=template.getMapper(UserMapper.class);events=template.getMapper(AssistantEventMapper.class);
        createTable(User.class);createTable(AssistantEvent.class);jdbc.execute("ALTER TABLE assistant_event ALTER COLUMN actor_id SET NOT NULL");
        var proxy=new ProxyFactory(new AssistantTelemetryService(events));proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));telemetry=(AssistantTelemetryService)proxy.getProxy();
        var reader=new ProxyFactory(new AssistantInsightsService(events,users,telemetry,CLOCK));reader.setProxyTargetClass(true);reader.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));service=(AssistantInsightsService)reader.getProxy();
        user(1,4);user(2,2);user(3,1);user(4,3);actor(2,"Admin");
    }
    @AfterEach void cleanup() {CurrentUserUtils.remove();jdbc.execute("DROP ALL OBJECTS");}
    private void actor(long id,String role) {var user=new CurrentUser();user.setId(id);user.setRoleKey(role);CurrentUserUtils.setUser(user);}
    private void user(long id,long role) {var user=new User();user.setId(id);user.setRoleId(role);user.setName("private name "+id);user.setIsEnable(true);user.setIsCanceled(false);user.setIsDelete(false);users.insert(user);}
    private AssistantEvent event(String stage,String outcome,long ms,LocalDateTime at,int calls,boolean known) {
        var e=new AssistantEvent();e.setId(next++);e.setActorId(1L);e.setStage(stage);e.setOutcome(outcome);e.setCreatedAt(at);e.setElapsedMs(ms);e.setAlternativeGroups(0);e.setModelCalls(calls);e.setUsageKnown(known);e.setModelName(calls>0?"test-model":null);
        if(known){e.setPromptTokens(100L);e.setCompletionTokens(10L);e.setTotalTokens(110L);}return e;
    }
    private void add(String stage,String outcome,long ms,int calls,boolean known) {events.insert(event(stage,outcome,ms,NOW.minusMinutes(1),calls,known));}
    @Test void emptyRangeHasNoInventedSuccessOrUsageAndFillsSydneyCalendarDays() {
        var report=service.get(null,null);assertEquals(LocalDate.of(2026,10,26),report.getEndDate());assertEquals(LocalDate.of(2026,10,20),report.getStartDate());assertEquals(7,report.getDays().size());
        assertEquals(0,report.getSearchRequests());assertEquals(0,report.getTotalTokens());assertNull(report.getSearchP50Ms());assertNull(report.getSearchP95Ms());assertNull(report.getFirstRecordedAt());assertTrue(report.getModels().isEmpty());
    }
    @Test void distinctStagesOutcomesAndReplaysDoNotInflateNewBookingSaves() {
        add("SEARCH","READY",1000,1,true);add("SEARCH","CLARIFY",20,1,false);add("SEARCH","UNAVAILABLE",0,1,false);add("SEARCH","PLAN",200,1,true);
        var noResults=event("SEARCH","NO_RESULTS",3000,NOW.minusMinutes(1),0,false);noResults.setAlternativeGroups(2);events.insert(noResults);
        add("CONFIRM_BOOKING","SAVED",10,0,false);add("CONFIRM_BOOKING","REPLAYED",5,0,false);add("CONFIRM_BOOKING","REJECTED",3,0,false);
        var change=event("CONFIRM_MANAGEMENT","SAVED",10,NOW.minusMinutes(1),0,false);change.setAction("RESCHEDULE");events.insert(change);
        add("PREPARE_BOOKING","REVIEW_READY",5,0,false);add("REPLACEMENTS","READY",50,0,false);
        var report=service.get(null,null);assertEquals(11,report.getObservedOperations());assertEquals(5,report.getSearchRequests());assertEquals(1,report.getSearchWithAlternatives());
        assertEquals(1,report.getNewBookingRequests());assertEquals(3,report.getBookingConfirmAttempts());assertEquals(1,report.getBookingConfirmReplays());assertEquals(1,report.getBookingConfirmFailures());
        assertEquals(200,report.getSearchP50Ms());assertEquals(3000,report.getSearchP95Ms());assertEquals(4,report.getModelCalls());assertEquals(2,report.getCallsWithKnownUsage());assertEquals(220,report.getTotalTokens());
        assertEquals(4,report.getModels().getFirst().getCalls());assertEquals(2,report.getModels().getFirst().getKnownUsage());
        var today=report.getDays().getLast();assertEquals(5,today.getSearches());assertEquals(1,today.getNewBookingRequests());assertEquals(1,today.getUnavailable());
        assertTrue(report.getStages().stream().anyMatch(r->"RESCHEDULE".equals(r.getAction())));
    }
    @Test void dateBoundsIncludeTheWholeHistoricalDayAndExcludeFutureObservations() {
        var start=NOW.toLocalDate().minusDays(1).atStartOfDay();
        events.insert(event("SEARCH","READY",1,start,0,false));events.insert(event("SEARCH","READY",2,start.plusDays(1).minusNanos(1000),0,false));
        events.insert(event("SEARCH","READY",3,start.minusSeconds(1),0,false));events.insert(event("SEARCH","READY",4,NOW,0,false));events.insert(event("SEARCH","READY",5,NOW.plusSeconds(1),0,false));
        assertEquals(2,service.get(start.toLocalDate(),start.toLocalDate()).getSearchRequests());assertEquals(1,service.get(NOW.toLocalDate(),NOW.toLocalDate()).getSearchRequests());
    }
    @Test void periodValidationAllows31DaysAndRejects32ReversedOrFutureDates() {
        assertEquals(31,service.get(NOW.toLocalDate().minusDays(30),NOW.toLocalDate()).getDays().size());
        for(var pair:List.of(List.of(NOW.toLocalDate().minusDays(31),NOW.toLocalDate()),List.of(NOW.toLocalDate(),NOW.toLocalDate().minusDays(1)),List.of(NOW.toLocalDate(),NOW.toLocalDate().plusDays(1))))
            assertThrows(BusinessException.class,()->service.get(pair.getFirst(),pair.getLast()));
    }
    @Test void onlyActiveMatchingAdminAndSuperAdminRolesCanReadAggregates() {
        actor(3,"SuAdmin");assertEquals(0,service.get(null,null).getObservedOperations());
        for(var pair:List.of(Map.entry(1L,"Stu"),Map.entry(4L,"PS"),Map.entry(1L,"Admin"),Map.entry(2L,"SuAdmin"))) {actor(pair.getKey(),pair.getValue());assertThrows(BusinessException.class,()->service.get(null,null));}
        actor(2,"Admin");jdbc.update("UPDATE `user` SET is_enable=FALSE WHERE id=2");assertThrows(BusinessException.class,()->service.get(null,null));
        CurrentUserUtils.remove();assertThrows(BusinessException.class,()->service.get(null,null));
    }
    @Test void nearestRankPercentilesUseOrderedDatabaseValuesRatherThanAnAverage() {
        for(int i=1;i<=20;i++)add("SEARCH",i%2==0?"UNAVAILABLE":"READY",i*10,0,false);
        var report=service.get(null,null);assertEquals(100,report.getSearchP50Ms());assertEquals(190,report.getSearchP95Ms());
    }
    @Test void missingTokenUsageIsDistinctFromAReportedZeroAndNoPrivateFieldsAreSerialized() {
        add("SEARCH","UNAVAILABLE",1,1,false);var zero=event("SEARCH","READY",2,NOW.minusMinutes(1),1,true);zero.setPromptTokens(0L);zero.setCompletionTokens(0L);zero.setTotalTokens(0L);events.insert(zero);
        var report=service.get(null,null);assertEquals(2,report.getModelCalls());assertEquals(1,report.getCallsWithKnownUsage());assertEquals(0,report.getTotalTokens());
        var encoded=new tools.jackson.databind.json.JsonMapper().writeValueAsString(report);
        for(String forbidden:List.of("actorId","private name","bookingId","feedback","apiKey","promptText"))assertFalse(encoded.contains(forbidden));
    }
    @Test void failedObservationTransactionDoesNotRollBackTheSurroundingBusinessWrite() {
        new TransactionTemplate(transactions).execute(status->{
            jdbc.update("UPDATE `user` SET name='Business committed' WHERE id=1");
            var bad=event("SEARCH","READY",1,NOW,0,false);bad.setActorId(null);
            assertThrows(RuntimeException.class,()->telemetry.record(bad));return null;
        });
        assertEquals("Business committed",users.selectById(1).getName());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM assistant_event",Integer.class));
        telemetry.dropped();assertEquals(1,service.get(null,null).getDroppedEventsSinceRestart());
    }
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
