package edu.unsw.service.assistant;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.BookingAssistantRequest;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.BookingAssistantResult.Status;
import edu.unsw.service.DictionaryService;
import edu.unsw.service.StudentSlotSearchService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingAssistantServiceTest {
    @Mock BookingIntentParser parser;
    @Mock DictionaryService dictionaries;
    @Mock UserMapper users;
    @Mock StudentSlotSearchService search;
    @Mock edu.unsw.service.BookingManagementService management;
    @Mock edu.unsw.service.BookingSubmissionService submissions;
    @Mock edu.unsw.service.AssessmentPlanningService planning;
    private BookingAssistantService service;
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 27, 14, 0);

    @BeforeAll
    static void lambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setup() {
        service = new BookingAssistantService(parser, dictionaries, users, search,
                Clock.fixed(Instant.parse("2026-10-21T23:00:00Z"), ZoneId.of("Australia/Sydney")), management, submissions, planning);
        lenient().when(parser.available()).thenReturn(true);
        Hospital hospital = new Hospital(); hospital.setName("Demo Hospital");
        Department department = new Department(); department.setName("Medicine");
        WbaType type = new WbaType(); type.setName("Mini-CEX");
        lenient().when(dictionaries.getEnabledHospitals()).thenReturn(List.of(hospital));
        lenient().when(dictionaries.getEnabledDepartments()).thenReturn(List.of(department));
        lenient().when(dictionaries.getEnabledWbaTypes()).thenReturn(List.of(type));
    }

    @Test
    void unconfiguredSearchDoesNotCallTheModelOrDatabase() {
        when(parser.available()).thenReturn(false);
        var result = service.search(request("Find a session"));
        assertEquals(Status.UNAVAILABLE, result.getStatus());
        assertNotNull(result.getTraceId());
        verify(parser, never()).parse(any());
        verifyNoInteractions(dictionaries, users, search);
    }

    @Test
    void clarificationPreservesQuestionAndDoesNotQuerySlots() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"CLARIFY\",\"question\":\"你想查询哪一天？\"}"));
        var result = service.search(request("下次有空吗"));
        assertEquals(Status.CLARIFY, result.getStatus());
        assertEquals("你想查询哪一天？", result.getMessage());
        verifyNoInteractions(search, users);
    }

    @Test
    void parsingFailureDoesNotInventAResultOrExposeProviderDetails() {
        when(parser.parse(any())).thenThrow(new IllegalStateException("private-provider-error"));
        var result = service.search(request("Find a session"));
        assertEquals(Status.UNAVAILABLE, result.getStatus());
        assertFalse(result.getMessage().contains("private-provider-error"));
        assertTrue(result.getCandidates().isEmpty());
        verifyNoInteractions(search);
    }

    @Test
    void unknownDictionaryValueRequiresClarification() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\",\"hospital\":\"Ghost Hospital\"}"));
        assertEquals(Status.CLARIFY, service.search(request("Ghost Hospital")).getStatus());
        verifyNoInteractions(search);
    }

    @Test
    void ambiguousSupervisorIsNotChosenArbitrarily() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\",\"supervisorName\":\"Wang\",\"supervisorMode\":\"REQUIRED\"}"));
        when(users.selectList(any())).thenReturn(List.of(supervisor(10L, "Dr Alice Wang"), supervisor(20L, "Dr Bob Wang")));
        assertEquals(Status.CLARIFY, service.search(request("必须 Wang")).getStatus());
        verifyNoInteractions(search);
    }

    @Test
    void hardSupervisorAndCanonicalConditionsAreAppliedAndIdsStayStrings() {
        when(parser.parse(any())).thenReturn(intent("""
                {"action":"SEARCH","hospital":"demo hospital","type":"Mini-CEX",
                 "supervisorName":"Wang","supervisorMode":"REQUIRED",
                 "startTime":"2026-10-27T00:00:00","endTime":"2026-10-28T00:00:00",
                 "earliestTime":"12:00","latestTime":"18:00","weekdays":[2]}
                """));
        when(users.selectList(any())).thenReturn(List.of(supervisor(10L, "Dr Wang")));
        long id = 2069245030206742539L;
        when(search.search(any())).thenReturn(page(List.of(slot(id, 10L, "Dr Wang", start))));
        var result = service.search(request("必须王老师，下周二下午"));
        assertEquals(Status.READY, result.getStatus());
        var condition = result.getConditions();
        assertEquals("Demo Hospital", condition.getHospital());
        assertEquals(10L, condition.getSupervisorId());
        assertEquals(LocalTime.NOON, condition.getEarliestTime());
        assertEquals(List.of(2), condition.getWeekdays());
        assertEquals(Long.toString(id), result.getCandidates().getFirst().getSlotId());
        assertEquals(start, result.getCandidates().getFirst().getStartTime());
        assertEquals("Actual Room", result.getCandidates().getFirst().getLocation());
    }

    @Test
    void preferredSupervisorBeyondFirstPageRanksAheadWithoutBecomingAHardConstraint() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\",\"supervisorName\":\"Wang\",\"supervisorMode\":\"PREFERRED\"}"));
        when(users.selectList(any())).thenReturn(List.of(supervisor(10L, "Dr Wang")));
        List<AssessmentSlotVo> slots = new ArrayList<>();
        for (int i = 1; i <= 15; i++) slots.add(slot(i, 20L, "Dr Smith", start.plusMinutes(i)));
        slots.add(slot(100L, 10L, "Dr Wang", start.plusDays(1)));
        when(search.search(any())).thenReturn(page(slots));
        var result = service.search(request("优先 Wang，其他老师也可以"));
        assertEquals(3, result.getCandidates().size());
        assertEquals("Dr Wang", result.getCandidates().getFirst().getSupervisorName());
        assertTrue(result.getCandidates().getFirst().isPreferredSupervisor());
        assertNull(result.getConditions().getSupervisorId());
        assertEquals(-1, result.getConditions().getPageQuantity());
        assertEquals("Dr Smith", result.getCandidates().get(1).getSupervisorName());
    }

    @Test
    void noMatchesMeansNoResultsRatherThanGeneratedAlternatives() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\"}"));
        when(search.search(any())).thenReturn(page(List.of()));
        var result = service.search(request("Find sessions"));
        assertEquals(Status.NO_RESULTS, result.getStatus());
        assertTrue(result.getCandidates().isEmpty());
    }

    @Test
    void writeRequestsDoNotReachSlotSearch() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"UNSUPPORTED\"}"));
        var result = service.search(request("取消我的预约"));
        assertEquals(Status.UNSUPPORTED, result.getStatus());
        verifyNoInteractions(search, users);
    }

    @Test
    void invalidQueryConstraintsAreReturnedAsClarification() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\"}"));
        when(search.search(any())).thenThrow(new BusinessException("Search window cannot exceed 90 days"));
        assertEquals(Status.CLARIFY, service.search(request("Find sessions")).getStatus());
    }

    @Test
    void duplicateOccurrencesAreDeduplicated() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\"}"));
        var slot = slot(1L, 10L, "Dr Wang", start);
        slot.setAvailableTimeList(List.of(List.of(start, start.plusMinutes(30)), List.of(start, start.plusMinutes(30))));
        when(search.search(any())).thenReturn(page(List.of(slot, slot)));
        assertEquals(1, service.search(request("Find sessions")).getCandidates().size());
    }

    @Test
    void followUpContextCarriesUserRequestsAndExplicitBusinessTime() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"CLARIFY\",\"question\":\"Which assessment?\"}"));
        var request = request("内科");
        request.setHistory(List.of("找下周二下午的评估"));
        service.search(request);
        ArgumentCaptor<BookingIntentParser.Context> capture = ArgumentCaptor.forClass(BookingIntentParser.Context.class);
        verify(parser).parse(capture.capture());
        assertEquals(List.of("找下周二下午的评估"), capture.getValue().history());
        assertEquals("Australia/Sydney", capture.getValue().timeZone());
        assertEquals(LocalDateTime.of(2026, 10, 22, 10, 0), capture.getValue().now());
    }

    @Test void managementUsesOriginalDatesAndNeverWritesOrAutomaticallySelectsABooking() {
        when(parser.parse(any())).thenReturn(intent("""
            {"action":"RESCHEDULE_BOOKING","startTime":"2026-10-27T00:00:00","endTime":"2026-10-28T00:00:00",
             "replacement":{"action":"SEARCH","startTime":"2026-10-29T12:00:00","endTime":"2026-10-29T18:00:00"}}
            """));
        var booking=new edu.unsw.domain.vo.BookingVo(); booking.setBookingId(500L);
        when(management.list(any(),isNull())).thenReturn(List.of(booking));
        var result=service.search(request("把周二预约改到周四下午"));
        assertEquals(Status.BOOKINGS,result.getStatus()); assertEquals("RESCHEDULE_BOOKING",result.getBookingAction());
        assertEquals(27,result.getConditions().getStartTime().getDayOfMonth()); assertEquals(29,result.getReplacementConditions().getStartTime().getDayOfMonth());
        assertEquals(500L,result.getBookings().getFirst().getBookingId());
        verifyNoInteractions(search,submissions); verify(management,never()).confirm(any());
    }

    @Test void managementWithoutDatesDoesNotInventOriginalDateAndPromptsSelectionWhenManyMatch() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"CANCEL_BOOKING\"}"));
        var matches=new ArrayList<edu.unsw.domain.vo.BookingVo>();
        for(int i=0;i<21;i++) matches.add(new edu.unsw.domain.vo.BookingVo());
        when(management.list(any(),isNull())).thenReturn(matches);
        var result=service.search(request("取消我的预约"));
        assertNull(result.getConditions().getStartTime()); assertNull(result.getConditions().getEndTime());
        assertTrue(result.isHasMoreBookings()); assertEquals(20,result.getBookings().size());
        verify(management,never()).prepare(any()); verifyNoInteractions(submissions);
    }

    @Test void planningReadsSavedTargetsAndActualProgressWithoutBookingOrUpdatingGoals() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"PLAN_ASSESSMENTS\"}"));
        var plan=new edu.unsw.domain.vo.AssessmentPlanVo(); plan.setConfigured(true); plan.setRemaining(3); plan.setToRequest(2);
        when(planning.summary()).thenReturn(plan);
        var result=service.search(request("帮我做评估规划"));
        assertEquals(Status.PLAN,result.getStatus()); assertSame(plan,result.getAssessmentPlan());
        verify(planning,never()).save(any()); verifyNoInteractions(search,submissions,management);
    }

    @Test void noExactMatchReturnsLabeledRealAlternativesAndPreservesOriginalConditions() {
        when(parser.parse(any())).thenReturn(intent("""
            {"action":"SEARCH","type":"Mini-CEX","supervisorName":"Wang","supervisorMode":"REQUIRED",
             "startTime":"2026-10-27T12:00:00","endTime":"2026-10-27T18:00:00"}
            """));
        when(users.selectList(any())).thenReturn(List.of(supervisor(10L,"Dr Wang")));
        when(search.search(any())).thenAnswer(call->{
            AssessmentSlotQueryDto q=call.getArgument(0);
            if(q.getStartTime().getDayOfMonth()==3) return page(List.of(slot(11,10,"Dr Wang",start.plusWeeks(1))));
            if(q.getSupervisorId()==null) return page(List.of(slot(12,20,"Dr Chen",start)));
            if(q.getStartTime().getHour()==0) return page(List.of(slot(13,10,"Dr Wang",start.withHour(9))));
            return page(List.of());
        });
        var result=service.search(request("下午必须Wang"));
        assertEquals(Status.NO_RESULTS,result.getStatus());assertTrue(result.getCandidates().isEmpty());
        assertEquals(List.of("DATE","TIME","SUPERVISOR"),result.getAlternatives().stream().map(a->a.getKind()).toList());
        assertEquals(10L,result.getConditions().getSupervisorId());assertEquals(12,result.getConditions().getStartTime().getHour());
        assertEquals("Mini-CEX",result.getAlternatives().getLast().getConditions().getType());
        verify(submissions,never()).submit(any(),any());verifyNoInteractions(management);
    }

    @Test void exactMatchesDoNotTriggerRelaxedSearches() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\"}"));
        when(search.search(any())).thenReturn(page(List.of(slot(1,10,"Dr Wang",start))));
        var result=service.search(request("Find session"));assertEquals(Status.READY,result.getStatus());assertTrue(result.getAlternatives().isEmpty());verify(search,times(1)).search(any());
    }

    @Test void alternateGroupsDeduplicateOccurrencesAndRejectUnbookableCandidates() {
        when(parser.parse(any())).thenReturn(intent("""
            {"action":"SEARCH","supervisorName":"Wang","startTime":"2026-10-27T12:00:00","endTime":"2026-10-27T18:00:00"}
            """));
        when(users.selectList(any())).thenReturn(List.of(supervisor(10,"Dr Wang")));
        var unavailable=slot(1,10,"Dr Wang",start);var valid=slot(2,10,"Dr Wang",start.plusWeeks(1));
        when(search.search(any())).thenReturn(page(List.of()),page(List.of(unavailable,valid,valid)));
        doAnswer(call->{edu.unsw.domain.dto.AssessmentBookingSaveDto q=call.getArgument(0);if(q.getAssessmentSlotId()==1L)throw new BusinessException("taken");return null;}).when(submissions).review(any());
        var result=service.search(request("Wang afternoon"));
        assertEquals(1,result.getAlternatives().size());assertEquals("2",result.getAlternatives().getFirst().getCandidates().getFirst().getSlotId());
    }

    @Test void alternativeFailureKeepsNoResultsAndDoesNotExposeInternalErrors() {
        when(parser.parse(any())).thenReturn(intent("{\"action\":\"SEARCH\"}"));
        when(search.search(any())).thenReturn(page(List.of())).thenThrow(new IllegalStateException("private SQL detail"));
        var result=service.search(request("Find session"));assertEquals(Status.NO_RESULTS,result.getStatus());assertTrue(result.getAlternatives().isEmpty());assertFalse(result.getMessage().contains("private SQL"));
    }

    private BookingIntent intent(String json) { return BookingIntent.fromJson(new JsonMapper().readTree(json)); }
    private BookingAssistantRequest request(String message) {
        var request = new BookingAssistantRequest(); request.setMessage(message); return request;
    }
    private User supervisor(long id, String name) { var user = new User(); user.setId(id); user.setName(name); return user; }
    private Page<AssessmentSlotVo> page(List<AssessmentSlotVo> values) {
        var page = new Page<AssessmentSlotVo>(1, -1); page.setRecords(values); return page;
    }
    private AssessmentSlotVo slot(long id, long creator, String name, LocalDateTime start) {
        var slot = new AssessmentSlotVo();
        slot.setId(id); slot.setVersion(0); slot.setCreator(creator); slot.setCreatorName(name);
        slot.setHospital("Demo Hospital"); slot.setDepartment("Medicine"); slot.setType("Mini-CEX");
        slot.setLocation("Actual Room"); slot.setAvailableTimeList(List.of(List.of(start, start.plusMinutes(30))));
        return slot;
    }
}
