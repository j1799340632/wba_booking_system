package edu.unsw.api.controller.supervisor;

import edu.unsw.core.config.exception.GlobalExceptionHandler;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.domain.dto.AssessmentCompletionDto;
import edu.unsw.domain.vo.SupervisorBookingResult;
import edu.unsw.service.AssessmentSlotService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssessmentSlotControllerTest {
    AssessmentSlotService service;MockMvc mvc;
    static final String BODY="{\"bookingId\":100,\"version\":0,\"score\":80,\"feedback\":\"Observed assessment\"}";
    @BeforeEach void setup() {
        service=mock(AssessmentSlotService.class);
        mvc=MockMvcBuilders.standaloneSetup(new AssessmentSlotController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void approvalUsesExplicitBookingVersionAndReturnsAReceipt() throws Exception {
        var receipt=new SupervisorBookingResult();receipt.setBookingId("100");receipt.setStatus(1);receipt.setVersion(1);
        when(service.approveSlot(1000L,100L,0)).thenReturn(receipt);
        mvc.perform(post("/supervisor/assessmentSlot/approveSlot").param("id","1000").param("bookingId","100").param("version","0"))
                .andExpect(jsonPath("$.data.bookingId").value("100")).andExpect(jsonPath("$.data.version").value(1));
        verify(service).approveSlot(1000L,100L,0);
    }
    @Test void completionAndCorrectionAreSeparateOperations() throws Exception {
        mvc.perform(post("/supervisor/assessmentSlot/markAssessmentCompleted").contentType("application/json").content(BODY)).andExpect(jsonPath("$.code").value(2000));
        mvc.perform(post("/supervisor/assessmentSlot/updateAssessmentResult").contentType("application/json").content(BODY)).andExpect(jsonPath("$.code").value(2000));
        verify(service).markAssessmentCompleted(any(AssessmentCompletionDto.class));verify(service).updateAssessmentResult(any(AssessmentCompletionDto.class));
    }
    @Test void missingVersionOutOfRangeScoresAndOversizedOrBlankFeedbackAreRejectedBeforeTheService() throws Exception {
        for(String body:java.util.List.of(BODY.replace("\"version\":0,",""),BODY.replace("\"score\":80","\"score\":101"),BODY.replace("Observed assessment"," "),BODY.replace("Observed assessment","x".repeat(256)))) {
            mvc.perform(post("/supervisor/assessmentSlot/markAssessmentCompleted").contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        }
        verifyNoInteractions(service);
    }
}
