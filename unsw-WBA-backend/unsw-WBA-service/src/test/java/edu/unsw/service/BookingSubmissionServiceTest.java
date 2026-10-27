package edu.unsw.service;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.*;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.domain.vo.BookingManagementVo;
import edu.unsw.service.assistant.BookingConfirmationService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real mapper SQL and Spring transactions, including competing submissions on separate connections. */
class BookingSubmissionServiceTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T23:00:00Z"), ZoneId.of("Australia/Sydney"));
    static final LocalDateTime SESSION = LocalDateTime.of(2026, 10, 27, 14, 0);
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    AssessmentBookingMapper bookings;
    AssessmentSlotMapper slots;
    UserMapper users;
    MessageMapper messages;
    RedissonUtils redis;
    BookingSubmissionService service;
    BookingConfirmationService confirmation;
    Map<String, Object> drafts;

    @BeforeEach void setup() {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        for (var mapper : List.of(AssessmentBookingMapper.class, AssessmentSlotMapper.class, UserMapper.class, MessageMapper.class)) config.addMapper(mapper);
        var template = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        bookings = template.getMapper(AssessmentBookingMapper.class); slots = template.getMapper(AssessmentSlotMapper.class);
        users = template.getMapper(UserMapper.class); messages = template.getMapper(MessageMapper.class);
        for (var type : List.of(AssessmentBooking.class, AssessmentSlot.class, User.class, Message.class)) createTable(type);
        jdbc.execute("CREATE UNIQUE INDEX uk_booking_request ON assessment_booking (creator,request_key)");
        redis = mock(RedissonUtils.class); drafts = new ConcurrentHashMap<>();
        doAnswer(call -> { drafts.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(redis).setObject(anyString(), any(), any(Duration.class));
        when(redis.getObject(anyString())).thenAnswer(call -> drafts.get(call.getArgument(0)));
        service = transactional(messages);
        confirmation = new BookingConfirmationService(service, redis, CLOCK);
        addUser(1, 4); addUser(2, 4); addUser(10, 3); addUser(20, 3);
        addSlot(100, 10, SESSION); student(1);
    }

    @AfterEach void cleanup() { CurrentUserUtils.remove(); jdbc.execute("DROP ALL OBJECTS"); }

    @Test void savesPendingRequestAndMessagesOnceAndReplaysAfterResponseLoss() {
        var request = request(100, SESSION, "request-1234567890");
        var first = service.submit(request, null);
        var retry = service.submit(request, null);
        assertEquals(0, first.getStatus()); assertEquals(first.getBookingId(), retry.getBookingId()); assertTrue(retry.isReplayed());
        assertEquals(SESSION, first.getStartTime()); assertEquals(1, count("assessment_booking")); assertEquals(2, count("message"));
        assertEquals(1, slots.selectById(100L).getStatus());
        request.setEndTime(SESSION.plusHours(1));
        assertThrows(BusinessException.class, () -> service.submit(request, null));
    }

    @Test void rejectsForgedOccurrencePastRangeAndStaleVersion() {
        for (var request : List.of(request(100, SESSION.plusMinutes(1), "forged-request-00001"),
                request(100, SESSION.minusDays(40), "past-request-0000001"))) {
            assertThrows(BusinessException.class, () -> service.submit(request, null));
        }
        var request = request(100, SESSION, "version-request-0001"); request.setSlotVersion(8);
        assertThrows(BusinessException.class, () -> service.submit(request, null));
        assertEquals(0, count("assessment_booking"));
    }

    @Test void disabledDeletedAndUnavailableAccountsCannotBeBooked() {
        jdbc.update("UPDATE assessment_slot SET is_enable=FALSE WHERE id=100");
        assertThrows(BusinessException.class, () -> service.submit(request(100, SESSION, "disabled-request-001"), null));
        jdbc.update("UPDATE assessment_slot SET is_enable=TRUE,is_delete=TRUE WHERE id=100");
        assertThrows(BusinessException.class, () -> service.submit(request(100, SESSION, "deleted-request-0001"), null));
        jdbc.update("UPDATE assessment_slot SET is_delete=FALSE WHERE id=100");
        jdbc.update("UPDATE `user` SET is_canceled=TRUE WHERE id=10");
        assertThrows(BusinessException.class, () -> service.submit(request(100, SESSION, "account-request-0001"), null));
    }

    @Test void onlyActualRecurrencesWithTheirExactDurationAreAccepted() {
        jdbc.update("UPDATE assessment_slot SET repeat_type=2,rrule='FREQ=WEEKLY;BYDAY=TU',start_time=?,end_time=?,repeat_start_time=?,repeat_end_time=? WHERE id=100",
                SESSION.minusWeeks(3), SESSION.minusWeeks(3).plusMinutes(30), SESSION.minusWeeks(3), SESSION.plusMonths(1));
        assertThrows(BusinessException.class, () -> service.submit(request(100, SESSION.plusDays(1), "wrong-weekday-000001"), null));
        var shortRequest = request(100, SESSION, "wrong-duration-00001"); shortRequest.setEndTime(SESSION.plusMinutes(15));
        assertThrows(BusinessException.class, () -> service.submit(shortRequest, null));
        var result = service.submit(request(100, SESSION, "real-recurrence-0001"), null);
        assertEquals(SESSION, result.getStartTime()); assertEquals(SESSION.plusMinutes(30), result.getEndTime());
        assertEquals(0, slots.selectById(100L).getStatus());
    }

    @Test void detectsStudentAndSupervisorOverlapAcrossDifferentSlotsButAllowsAdjacency() {
        service.submit(request(100, SESSION, "first-request-000001"), null);
        addSlot(101, 20, SESSION.plusMinutes(15));
        assertThrows(BusinessException.class, () -> service.submit(request(101, SESSION.plusMinutes(15), "student-overlap-0001"), null));
        student(2); addSlot(102, 10, SESSION.plusMinutes(15));
        assertThrows(BusinessException.class, () -> service.submit(request(102, SESSION.plusMinutes(15), "mentor-overlap-00001"), null));
        addSlot(103, 10, SESSION.plusMinutes(30));
        assertEquals(0, service.submit(request(103, SESSION.plusMinutes(30), "adjacent-request-001"), null).getStatus());
    }

    @Test void concurrentSameRequestCreatesOneBookingAndTwoMessages() throws Exception {
        var results = race(() -> submitAs(1, 100, "same-request-0000001"), () -> submitAs(1, 100, "same-request-0000001"));
        assertEquals(2, results.stream().filter(BookingVo.class::isInstance).count());
        assertEquals(1, results.stream().map(BookingVo.class::cast).map(BookingVo::getBookingId).distinct().count());
        assertEquals(1, count("assessment_booking")); assertEquals(2, count("message"));
    }

    @Test void competingStudentsForOneSlotHaveOneWinner() throws Exception {
        var results = race(() -> submitAs(1, 100, "student-one-00000001"), () -> submitAs(2, 100, "student-two-00000002"));
        assertEquals(1, results.stream().filter(BookingVo.class::isInstance).count());
        assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count());
        assertEquals(1, count("assessment_booking")); assertEquals(2, count("message"));
    }

    @Test void competingOverlappingSlotsSerializeOnSharedSupervisor() throws Exception {
        addSlot(101, 10, SESSION);
        var results = race(() -> submitAs(1, 100, "shared-mentor-one01"), () -> submitAs(2, 101, "shared-mentor-two02"));
        assertEquals(1, results.stream().filter(BookingVo.class::isInstance).count());
        assertEquals(1, count("assessment_booking"));
    }

    @Test void rollsBackBookingAndSlotWhenDurableNotificationInsertFails() {
        var broken = mock(MessageMapper.class);
        when(broken.insert(any(Message.class))).thenThrow(new IllegalStateException("fixture notification failure"));
        var failing = transactional(broken);
        assertThrows(IllegalStateException.class, () -> failing.submit(request(100, SESSION, "rollback-request-001"), null));
        assertEquals(0, count("assessment_booking")); assertEquals(0, slots.selectById(100L).getStatus());
    }

    @Test void liveNotificationFailureDoesNotUndoSavedBookingOrDuplicateMessagesOnRetry() {
        when(redis.publish(anyString(), any(NotificationDto.class))).thenThrow(new IllegalStateException("fixture live failure"));
        var request = request(100, SESSION, "live-failure-0000001");
        assertEquals("LIVE_DELAYED", service.submit(request, null).getNotificationStatus());
        assertTrue(service.submit(request, null).isReplayed());
        assertEquals(1, count("assessment_booking")); assertEquals(2, count("message"));
    }

    @Test void serverOwnedReviewIsBoundToStudentAndRequiresExplicitConfirmation() {
        var draft = confirmation.prepare(request(100, SESSION, "unused-client-key001"));
        assertEquals(0, count("assessment_booking"));
        var confirm = confirmation(draft.getDraftId()); confirm.setConfirmed(false);
        assertThrows(BusinessException.class, () -> confirmation.confirm(confirm));
        confirm.setConfirmed(true); student(2);
        assertThrows(BusinessException.class, () -> confirmation.confirm(confirm));
        student(1); var receipt = confirmation.confirm(confirm);
        drafts.clear(); // The review can expire or Redis can lose its entry after the commit.
        assertEquals(receipt.getBookingId(), confirmation.confirm(confirm).getBookingId());
        assertEquals(1, count("assessment_booking"));
    }

    @Test void reviewRejectsExpiryAndChangesEvenWhenLegacyEditsDidNotIncrementVersion() {
        var draft = confirmation.prepare(request(100, SESSION, "review-change-00001"));
        jdbc.update("UPDATE assessment_slot SET location='Different building' WHERE id=100");
        assertThrows(BusinessException.class, () -> confirmation.confirm(confirmation(draft.getDraftId())));
        var stored = (BookingConfirmationService.StoredDraft) drafts.values().iterator().next();
        stored.setExpiresAt(LocalDateTime.now(CLOCK).minusSeconds(1));
        assertThrows(BusinessException.class, () -> confirmation.confirm(confirmation(draft.getDraftId())));
        assertEquals(0, count("assessment_booking"));
    }

    @Test void deletedOrCanceledReceiptIsReplayedRatherThanBookedAgainUnderSameKey() {
        var request = request(100, SESSION, "canceled-retry-00001");
        var first = service.submit(request, null);
        jdbc.update("UPDATE assessment_booking SET is_delete=TRUE,status=4 WHERE id=?", first.getBookingId());
        jdbc.update("UPDATE assessment_slot SET status=0 WHERE id=100");
        assertEquals(4, service.submit(request, null).getStatus());
        request.setRequestKey("new-after-cancel-001");
        assertNotEquals(first.getBookingId(), service.submit(request, null).getBookingId());
        assertEquals(2, count("assessment_booking"));
    }

    @Test void rejectsNonStudentIdentityAndAmbiguousOrMissingDstWallTimes() {
        var actor = new CurrentUser(); actor.setId(1L); actor.setRoleKey("Admin"); CurrentUserUtils.setUser(actor);
        assertThrows(BusinessException.class, () -> service.submit(request(100, SESSION, "role-check-00000001"), null));
        student(1);
        for (var start : List.of(LocalDateTime.of(2027, 10, 3, 2, 15), LocalDateTime.of(2027, 4, 4, 2, 15))) {
            jdbc.update("UPDATE assessment_slot SET start_time=?,end_time=? WHERE id=100", start, start.plusMinutes(30));
            assertThrows(BusinessException.class, () -> service.submit(request(100, start, "dst-check-000000001"), null));
        }
        assertEquals(0, count("assessment_booking"));
    }

    private BookingSubmissionService transactional(MessageMapper messageMapper) {
        var target = new BookingSubmissionService(bookings, slots, users, messageMapper, redis, CLOCK);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        return (BookingSubmissionService) proxy.getProxy();
    }

    @Test void cancellationKeepsHistoryAndReplaysAfterLostResponseAndExpiredDraft() {
        var original=service.submit(request(100,SESSION,"manage-original-001"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"CANCEL",null));
        assertEquals(0,bookings.selectById(original.getBookingId()).getStatus());
        var unchecked=confirmation(draft.getDraftId()); unchecked.setConfirmed(false);
        assertThrows(BusinessException.class,()->manager.confirm(unchecked));
        student(2); assertThrows(BusinessException.class,()->manager.confirm(confirmation(draft.getDraftId())));
        assertThrows(BusinessException.class,()->manager.prepare(change(original,"CANCEL",null)));
        student(1); var receipt=manager.confirm(confirmation(draft.getDraftId()));
        assertEquals(4,receipt.getOriginal().getStatus()); assertEquals(0,slots.selectById(100L).getStatus());
        assertFalse(bookings.selectById(original.getBookingId()).getIsDelete());
        drafts.clear(); assertTrue(manager.confirm(confirmation(draft.getDraftId())).isReplayed());
        assertEquals(1,count("assessment_booking")); assertEquals(4,count("message"));
    }

    @Test void rescheduleIsAtomicRetainsOriginalHistoryAndCreatesOnePendingReplacement() {
        addSlot(101,20,SESSION.plusDays(1));
        var original=service.submit(request(100,SESSION,"reschedule-orig-001"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION.plusDays(1),"ignored-client-key01")));
        assertEquals(1,count("assessment_booking"));
        var receipt=manager.confirm(confirmation(draft.getDraftId()));
        assertEquals("RESCHEDULE",receipt.getAction()); assertEquals(4,receipt.getOriginal().getStatus()); assertEquals(0,receipt.getNewBooking().getStatus());
        assertEquals(receipt.getNewBooking().getBookingId(),bookings.selectById(original.getBookingId()).getReplacementBookingId());
        assertEquals(0,slots.selectById(100L).getStatus()); assertEquals(1,slots.selectById(101L).getStatus());
        drafts.clear(); assertEquals(receipt.getNewBooking().getBookingId(),manager.confirm(confirmation(draft.getDraftId())).getNewBooking().getBookingId());
        assertEquals(2,count("assessment_booking")); assertEquals(6,count("message"));
    }

    @Test void takenReplacementRollsBackCancellationAndPreservesOriginalOccupancy() {
        addSlot(101,20,SESSION.plusDays(1));
        var original=service.submit(request(100,SESSION,"rollback-original01"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION.plusDays(1),"ignored-key-00000001")));
        student(2); service.submit(request(101,SESSION.plusDays(1),"other-wins-target01"),null); student(1);
        assertThrows(BusinessException.class,()->manager.confirm(confirmation(draft.getDraftId())));
        assertEquals(0,bookings.selectById(original.getBookingId()).getStatus());
        assertNull(bookings.selectById(original.getBookingId()).getManagementKey());
        assertEquals(1,slots.selectById(100L).getStatus()); assertEquals(2,count("assessment_booking")); assertEquals(4,count("message"));
    }

    @Test void overlappingReplacementExcludesOnlyTheSelectedOriginal() {
        addSlot(101,20,SESSION);
        var original=service.submit(request(100,SESSION,"overlap-original001"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION,"ignored-overlap-key01")));
        assertEquals(0,manager.confirm(confirmation(draft.getDraftId())).getNewBooking().getStatus());
        assertEquals(2,count("assessment_booking"));
    }

    @Test void confirmedFortyEightHourRuleUsesSydneyInstantAndIsRecheckedAtConfirmation() {
        var original=service.submit(request(100,SESSION,"time-rule-original1"),null);
        jdbc.update("UPDATE assessment_booking SET status=1 WHERE id=?",original.getBookingId());
        var early=management(messages,at(SESSION.minusHours(48)));
        var draft=early.prepare(change(original,"CANCEL",null)); // exactly 48 hours is allowed
        var late=management(messages,at(SESSION.minusHours(47)));
        assertThrows(BusinessException.class,()->late.prepare(change(original,"CANCEL",null)));
        assertThrows(BusinessException.class,()->late.confirm(confirmation(draft.getDraftId())));
        assertEquals(1,bookings.selectById(original.getBookingId()).getStatus());
        jdbc.update("UPDATE assessment_booking SET status=0 WHERE id=?",original.getBookingId());
        var pending=management(messages,at(SESSION.minusMinutes(1)));
        assertNotNull(pending.prepare(change(original,"CANCEL",null)));
        assertThrows(BusinessException.class,()->management(messages,at(SESSION)).prepare(change(original,"CANCEL",null)));
    }

    @Test void expiredReviewAndChangedBookingOrDisplayDetailsCannotBeConfirmed() {
        var original=service.submit(request(100,SESSION,"stale-original-0001"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"CANCEL",null));
        var stored=(BookingManagementService.Draft)drafts.get("booking:management:1:"+draft.getDraftId());
        stored.setExpiresAt(LocalDateTime.now(CLOCK).minusSeconds(1));
        assertThrows(BusinessException.class,()->manager.confirm(confirmation(draft.getDraftId())));
        var current=manager.prepare(change(original,"CANCEL",null));
        jdbc.update("UPDATE assessment_slot SET location='Changed room' WHERE id=100");
        assertThrows(BusinessException.class,()->manager.confirm(confirmation(current.getDraftId())));
        var changed=manager.prepare(change(original,"CANCEL",null));
        jdbc.update("UPDATE assessment_booking SET status=1,version=1 WHERE id=?",original.getBookingId());
        assertThrows(BusinessException.class,()->manager.confirm(confirmation(changed.getDraftId())));
        assertEquals(1,count("assessment_booking")); assertEquals(2,count("message"));
    }

    @Test void notificationFailureRollsBackBothSidesOfReschedule() {
        addSlot(101,20,SESSION.plusDays(1));
        var original=service.submit(request(100,SESSION,"notice-original-001"),null);
        var broken=mock(MessageMapper.class);
        when(broken.insert(any(Message.class))).thenThrow(new IllegalStateException("fixture failure after replacement insert"));
        var manager=management(broken,CLOCK);
        var draft=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION.plusDays(1),"ignored-notice-key01")));
        assertThrows(IllegalStateException.class,()->manager.confirm(confirmation(draft.getDraftId())));
        assertEquals(0,bookings.selectById(original.getBookingId()).getStatus());
        assertEquals(1,slots.selectById(100L).getStatus()); assertEquals(0,slots.selectById(101L).getStatus());
        assertEquals(1,count("assessment_booking")); assertEquals(2,count("message"));
    }

    @Test void concurrentDuplicateManagementConfirmationsReturnOneReceipt() throws Exception {
        addSlot(101,20,SESSION.plusDays(1));
        var original=service.submit(request(100,SESSION,"concurrent-orig-001"),null);
        var manager=management(messages,CLOCK);
        var draft=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION.plusDays(1),"ignored-race-key0001")));
        var result=race(()->manager.confirm(confirmation(draft.getDraftId())),()->manager.confirm(confirmation(draft.getDraftId())));
        assertEquals(1,result.stream().map(BookingManagementVo.class::cast).map(x->x.getNewBooking().getBookingId()).distinct().count());
        assertEquals(2,count("assessment_booking")); assertEquals(6,count("message"));
    }

    @Test void competingCancellationAndRescheduleHaveOneWinner() throws Exception {
        addSlot(101,20,SESSION.plusDays(1));
        var original=service.submit(request(100,SESSION,"competing-orig-0001"),null);
        var manager=management(messages,CLOCK);
        var cancel=manager.prepare(change(original,"CANCEL",null));
        var reschedule=manager.prepare(change(original,"RESCHEDULE",request(101,SESSION.plusDays(1),"ignored-compete-key1")));
        var results=race(()->managementAttempt(manager,cancel.getDraftId()),()->managementAttempt(manager,reschedule.getDraftId()));
        assertEquals(1,results.stream().filter(BookingManagementVo.class::isInstance).count());
        assertEquals(1,results.stream().filter(BusinessException.class::isInstance).count());
        assertEquals(4,bookings.selectById(original.getBookingId()).getStatus());
    }

    @Test void terminalBookingsStaleVersionsAndOtherRolesCannotBeChanged() {
        var original=service.submit(request(100,SESSION,"terminal-original01"),null);
        var manager=management(messages,CLOCK);
        for(int status:List.of(2,3,4)) {
            jdbc.update("UPDATE assessment_booking SET status=? WHERE id=?",status,original.getBookingId());
            assertThrows(BusinessException.class,()->manager.prepare(change(original,"CANCEL",null)));
        }
        jdbc.update("UPDATE assessment_booking SET status=0,version=2 WHERE id=?",original.getBookingId());
        assertThrows(BusinessException.class,()->manager.prepare(change(original,"CANCEL",null)));
        var actor=new CurrentUser(); actor.setId(1L); actor.setRoleKey("Admin"); CurrentUserUtils.setUser(actor);
        assertThrows(BusinessException.class,()->manager.cancelDirect(original.getBookingId()));
        assertThrows(BusinessException.class,()->manager.list(new AssessmentSlotQueryDto(),null));
    }

    @Test void directCancellationUsesSameRulesAndDoesNotDuplicateMessages() {
        var original=service.submit(request(100,SESSION,"direct-original-001"),null);
        var manager=management(messages,CLOCK);
        assertTrue(manager.cancelDirect(original.getBookingId())); assertTrue(manager.cancelDirect(original.getBookingId()));
        assertEquals(4,bookings.selectById(original.getBookingId()).getStatus()); assertEquals(4,count("message"));
    }

    @Test void managementListFiltersOnlyOwnersActualOccurrenceAndKeepsCancelledHistory() {
        var original=service.submit(request(100,SESSION,"list-original-00001"),null);
        addSlot(101,20,SESSION.plusDays(1)); student(2); service.submit(request(101,SESSION.plusDays(1),"list-other-00000001"),null); student(1);
        var manager=management(messages,CLOCK);
        var query=new AssessmentSlotQueryDto(); query.setStartTime(SESSION.minusHours(1)); query.setEndTime(SESSION.plusHours(1)); query.setWeekdays(List.of(2));
        assertEquals(List.of(original.getBookingId()),manager.list(query,null).stream().map(BookingVo::getBookingId).toList());
        manager.cancelDirect(original.getBookingId()); assertEquals(4,manager.list(query,4).getFirst().getStatus());
        query.setWeekdays(List.of(3)); assertTrue(manager.list(query,null).isEmpty());
    }

    @Test void liveFailureKeepsSuccessfulManagementReceiptAndDurableNotifications() {
        var original=service.submit(request(100,SESSION,"live-manage-orig001"),null);
        var manager=management(messages,CLOCK); var draft=manager.prepare(change(original,"CANCEL",null));
        when(redis.publish(anyString(),any(NotificationDto.class))).thenThrow(new IllegalStateException("fixture live unavailable"));
        assertEquals("LIVE_DELAYED",manager.confirm(confirmation(draft.getDraftId())).getNotificationStatus());
        assertTrue(manager.confirm(confirmation(draft.getDraftId())).isReplayed()); assertEquals(4,count("message"));
    }

    @Test void cancellingOneRecurrencePreservesOtherOccurrenceAndTemplateAvailability() {
        jdbc.update("UPDATE assessment_slot SET repeat_type=2,rrule='FREQ=WEEKLY;BYDAY=TU',repeat_start_time=?,repeat_end_time=? WHERE id=100",SESSION,SESSION.plusWeeks(4));
        var first=service.submit(request(100,SESSION,"recurrence-first001"),null);
        student(2); var later=service.submit(request(100,SESSION.plusWeeks(1),"recurrence-later001"),null); student(1);
        var manager=management(messages,CLOCK); manager.cancelDirect(first.getBookingId());
        assertEquals(0,slots.selectById(100L).getStatus()); assertEquals(0,bookings.selectById(later.getBookingId()).getStatus());
        assertDoesNotThrow(()->service.submit(request(100,SESSION,"recurrence-reuse001"),null));
        assertThrows(BusinessException.class,()->service.submit(request(100,SESSION.plusWeeks(1),"recurrence-taken001"),null));
    }

    private BookingManagementService management(MessageMapper mapper,Clock clock) {
        var target=new BookingManagementService(bookings,slots,users,mapper,service,redis,clock);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        return (BookingManagementService)proxy.getProxy();
    }
    private static Clock at(LocalDateTime time) { return Clock.fixed(time.atZone(CLOCK.getZone()).toInstant(),CLOCK.getZone()); }
    private static BookingManagementRequest change(BookingVo original,String action,AssessmentBookingSaveDto replacement) {
        var request=new BookingManagementRequest(); request.setBookingId(original.getBookingId()); request.setBookingVersion(original.getVersion());
        request.setAction(BookingManagementRequest.Action.valueOf(action)); request.setReplacement(replacement); return request;
    }
    private Object managementAttempt(BookingManagementService manager,String draft) {
        try { return manager.confirm(confirmation(draft)); } catch(BusinessException error) { return error; }
    }
    private Object submitAs(long studentId, long slotId, String key) {
        student(studentId);
        try { return service.submit(request(slotId, SESSION, key), null); }
        catch (BusinessException error) { return error; }
        finally { CurrentUserUtils.remove(); }
    }
    private List<Object> race(Callable<Object> a, Callable<Object> b) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return a.call(); });
            var second = pool.submit(() -> { gate.await(); return b.call(); }); gate.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
    private static AssessmentBookingSaveDto request(long id, LocalDateTime start, String key) {
        var request = new AssessmentBookingSaveDto(); request.setAssessmentSlotId(id); request.setSlotVersion(0);
        request.setStartTime(start); request.setEndTime(start.plusMinutes(30)); request.setRequestKey(key); return request;
    }
    private static BookingConfirmationRequest confirmation(String id) {
        var request = new BookingConfirmationRequest(); request.setDraftId(id); request.setConfirmed(true); return request;
    }
    private static void student(long id) { var user = new CurrentUser(); user.setId(id); user.setRoleKey("Stu"); CurrentUserUtils.setUser(user); }
    private void addUser(long id, long role) {
        var user = new User(); user.setId(id); user.setRoleId(role); user.setName("Demo " + id);
        user.setIsEnable(true); user.setIsCanceled(false); user.setIsDelete(false); users.insert(user);
    }
    private void addSlot(long id, long supervisor, LocalDateTime start) {
        var slot = new AssessmentSlot(); slot.setId(id); slot.setCreator(supervisor); slot.setStartTime(start); slot.setEndTime(start.plusMinutes(30));
        slot.setIsEnable(true); slot.setIsDelete(false); slot.setStatus(0); slot.setVersion(0); slot.setRepeatType(0);
        slot.setHospital("Demo Hospital"); slot.setDepartment("Medicine"); slot.setLocation("Room A"); slot.setType("Mini-CEX"); slots.insert(slot);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void createTable(Class<?> type) {
        var table = TableInfoHelper.getTableInfo(type); var columns = new ArrayList<String>();
        columns.add(table.getKeyColumn() + " BIGINT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> value = field.getPropertyType();
            String sql = value == LocalDateTime.class ? "TIMESTAMP" : value == LocalTime.class ? "TIME"
                    : value == Long.class ? "BIGINT" : value == Integer.class ? "INTEGER"
                    : value == Boolean.class ? "BOOLEAN" : value == Double.class ? "DOUBLE" : "VARCHAR(4096)";
            columns.add(field.getColumn() + " " + sql);
        }
        jdbc.execute("CREATE TABLE " + table.getTableName() + " (" + String.join(",", columns) + ")");
    }
}
