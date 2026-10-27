package edu.unsw.api.aop;

import edu.unsw.api.controller.student.BookingAssistantController;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.entity.AssistantEvent;
import edu.unsw.domain.vo.*;
import edu.unsw.service.*;
import edu.unsw.service.assistant.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantObservationAspectTest {
    BookingAssistantService assistant;BookingConfirmationService confirmation;BookingManagementService management;
    AssistantTelemetryService telemetry;BookingAssistantController controller;
    @BeforeEach void setup() {
        assistant=mock(BookingAssistantService.class);confirmation=mock(BookingConfirmationService.class);management=mock(BookingManagementService.class);telemetry=mock(AssistantTelemetryService.class);
        var factory=new AspectJProxyFactory(new BookingAssistantController(assistant,confirmation,management));factory.setProxyTargetClass(true);
        factory.addAspect(new AssistantObservationAspect(telemetry,Clock.fixed(Instant.parse("2026-10-25T14:30:00Z"),ZoneId.of("Australia/Sydney"))));controller=factory.getProxy();actor("Stu");
    }
    private void actor(String role) {var user=new CurrentUser();user.setId(1L);user.setRoleKey(role);CurrentUserUtils.setUser(user);}
    @AfterEach void cleanup() {CurrentUserUtils.remove();}
    private List<AssistantEvent> captured(int count) {var capture=ArgumentCaptor.forClass(AssistantEvent.class);verify(telemetry,times(count)).record(capture.capture());return capture.getAllValues();}
    @Test void recordsResultMetadataAndReportedUsageWithoutConversationOrSessionDetails() {
        when(assistant.search(any())).thenAnswer(invocation->{
            BookingModelObservation.attempted("stub-model");BookingModelObservation.usage(100,20,120);
            var result=new BookingAssistantResult();result.setStatus(BookingAssistantResult.Status.NO_RESULTS);result.setMessage("private conversation");result.setAlternatives(List.of(new BookingAssistantResult.Alternative()));return result;
        });
        controller.search(new BookingAssistantRequest());var event=captured(1).getFirst();assertEquals("SEARCH",event.getStage());assertEquals("NO_RESULTS",event.getOutcome());assertEquals(1,event.getAlternativeGroups());
        assertEquals(1,event.getModelCalls());assertTrue(event.getUsageKnown());assertEquals(120,event.getTotalTokens());assertEquals(LocalDateTime.of(2026,10,26,1,30),event.getCreatedAt());
        var encoded=new tools.jackson.databind.json.JsonMapper().writeValueAsString(event);assertFalse(encoded.contains("private conversation"));
    }
    @Test void scopedUsageDoesNotLeakIntoReviewOrSubsequentRequestsOnTheSameThread() {
        when(assistant.search(any())).thenAnswer(invocation->{BookingModelObservation.attempted("stub");BookingModelObservation.usage(100,20,120);var result=new BookingAssistantResult();result.setStatus(BookingAssistantResult.Status.READY);return result;});
        when(confirmation.prepare(any())).thenReturn(new BookingDraftVo());controller.search(new BookingAssistantRequest());
        BookingModelObservation.attempted("outside a request");BookingModelObservation.usage(999,999,1998);
        controller.prepare(new AssessmentBookingSaveDto());var second=captured(2).getLast();assertEquals("PREPARE_BOOKING",second.getStage());assertEquals("REVIEW_READY",second.getOutcome());assertEquals(0,second.getModelCalls());assertFalse(second.getUsageKnown());assertNull(second.getTotalTokens());
    }
    @Test void freshAndReplayedConfirmationsRemainSeparateAndManagementHasAnAction() {
        var fresh=new BookingVo();var replay=new BookingVo();replay.setReplayed(true);
        when(confirmation.confirm(any())).thenReturn(fresh,replay);controller.confirm(new BookingConfirmationRequest());controller.confirm(new BookingConfirmationRequest());
        var change=new BookingManagementVo();change.setAction("RESCHEDULE");change.setReplayed(true);when(management.confirm(any())).thenReturn(change);controller.confirmManagement(new BookingConfirmationRequest());
        var events=captured(3);assertEquals(List.of("SAVED","REPLAYED","REPLAYED"),events.stream().map(AssistantEvent::getOutcome).toList());assertEquals("RESCHEDULE",events.getLast().getAction());
    }
    @Test void businessRejectionsAreObservedAndTheOriginalExceptionIsPreserved() {
        var failure=new BusinessException("private rejection details");when(confirmation.confirm(any())).thenThrow(failure);
        assertSame(failure,assertThrows(BusinessException.class,()->controller.confirm(new BookingConfirmationRequest())));assertEquals("REJECTED",captured(1).getFirst().getOutcome());
    }
    @Test void unexpectedFailureIsObservedWithoutExposingItsBody() {
        var failure=new IllegalStateException("secret provider body");when(assistant.search(any())).thenThrow(failure);
        assertSame(failure,assertThrows(IllegalStateException.class,()->controller.search(new BookingAssistantRequest())));var event=captured(1).getFirst();assertEquals("ERROR",event.getOutcome());assertFalse(new tools.jackson.databind.json.JsonMapper().writeValueAsString(event).contains("secret provider body"));
    }
    @Test void telemetryFailureDoesNotReplaceTheSavedBusinessReceipt() {
        var receipt=new BookingVo();receipt.setBookingId(100L);when(confirmation.confirm(any())).thenReturn(receipt);
        doThrow(new IllegalStateException("telemetry unavailable")).when(telemetry).record(any());
        assertSame(receipt,controller.confirm(new BookingConfirmationRequest()).getData());verify(telemetry).dropped();
    }
    @Test void otherRolesAndUnauthenticatedRequestsAreNotRecordedAsStudentUsage() {
        for(String role:List.of("Admin","SuAdmin","PS")){actor(role);assertThrows(BusinessException.class,()->controller.search(new BookingAssistantRequest()));}
        CurrentUserUtils.remove();assertThrows(BusinessException.class,()->controller.search(new BookingAssistantRequest()));verifyNoInteractions(telemetry);
    }
}
