package edu.unsw.api.controller.student;

import edu.unsw.core.config.exception.GlobalExceptionHandler;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.dto.BookingAssistantRequest;
import edu.unsw.domain.vo.BookingAssistantResult;
import edu.unsw.service.assistant.BookingAssistantService;
import edu.unsw.service.assistant.BookingConfirmationService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingAssistantControllerTest {
    private BookingAssistantService assistant;
    private BookingConfirmationService confirmation;
    private edu.unsw.service.BookingManagementService management;
    private MockMvc mvc;
    private final JsonMapper json = new JsonMapper();

    @BeforeEach
    void setup() {
        assistant = mock(BookingAssistantService.class);
        confirmation = mock(BookingConfirmationService.class);
        management=mock(edu.unsw.service.BookingManagementService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookingAssistantController(assistant, confirmation, management))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearUser() { CurrentUserUtils.remove(); }

    @Test
    void onlyStudentIdentityCanUseAssistantEvenIfOtherRolesInheritStudentPermissions() throws Exception {
        for (String role : List.of("PS", "Admin", "SuAdmin")) {
            var user = new CurrentUser(); user.setRoleKey(role); CurrentUserUtils.setUser(user);
            mvc.perform(post("/student/bookingAssistant/search").contentType("application/json").content("{\"message\":\"Find sessions\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
        }
        CurrentUserUtils.remove();
        mvc.perform(post("/student/bookingAssistant/search").contentType("application/json").content("{\"message\":\"Find sessions\"}"))
                .andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
        verifyNoInteractions(assistant);
    }

    @Test
    void acceptsStudentFollowUpAndReturnsStructuredReadOnlyResult() throws Exception {
        student();
        var result = new BookingAssistantResult();
        result.setStatus(BookingAssistantResult.Status.CLARIFY);
        result.setMessage("Which assessment type?");
        when(assistant.search(any())).thenReturn(result);
        mvc.perform(post("/student/bookingAssistant/search").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("message", "内科", "history", List.of("下周二下午")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLARIFY"))
                .andExpect(jsonPath("$.data.candidates").isEmpty());
        var capture = ArgumentCaptor.forClass(BookingAssistantRequest.class);
        verify(assistant).search(capture.capture());
        assertEquals("内科", capture.getValue().getMessage());
        assertEquals(List.of("下周二下午"), capture.getValue().getHistory());
    }

    @Test
    void rejectsEmptyOversizedAndExcessiveHistoryBeforeCallingService() throws Exception {
        student();
        for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("message", " "),
                Map.of("message", "a".repeat(1501)), Map.of("message", "Find", "history", Collections.nCopies(7, "old")),
                Map.of("message", "Find", "history", List.of("")))) {
            mvc.perform(post("/student/bookingAssistant/search").contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        }
        verifyNoInteractions(assistant);
    }

    @Test void managementRequiresStudentValidSelectionAndExplicitConfirmation() throws Exception {
        student();
        for(String body:List.of("{}","{\"bookingId\":1,\"bookingVersion\":0}","{\"bookingId\":1,\"bookingVersion\":-1,\"action\":\"CANCEL\"}"))
            mvc.perform(post("/student/bookingAssistant/management/prepare").contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        mvc.perform(post("/student/bookingAssistant/management/confirm").contentType("application/json").content("{\"draftId\":\"12345678-1234-1234-1234-123456789012\",\"confirmed\":false}")).andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        for(String role:List.of("PS","Admin","SuAdmin")) {
            var user=new CurrentUser(); user.setRoleKey(role); CurrentUserUtils.setUser(user);
            mvc.perform(post("/student/bookingAssistant/management/prepare").contentType("application/json").content("{\"bookingId\":1,\"bookingVersion\":0,\"action\":\"CANCEL\"}")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
            mvc.perform(post("/student/bookingAssistant/management/confirm").contentType("application/json").content("{\"draftId\":\"12345678-1234-1234-1234-123456789012\",\"confirmed\":true}")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
            mvc.perform(post("/student/bookingAssistant/management/replacements").contentType("application/json").content("{\"bookingId\":1,\"conditions\":{}}")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
        }
        verifyNoInteractions(management);
    }

    private void student() { var user = new CurrentUser(); user.setRoleKey("Stu"); CurrentUserUtils.setUser(user); }

    @Test
    void confirmationRequiresAValidDraftAndExplicitTrueBeforeServiceCall() throws Exception {
        student();
        for (String body : List.of("{}", "{\"draftId\":\"not-a-draft\",\"confirmed\":true}",
                "{\"draftId\":\"12345678-1234-1234-1234-123456789012\"}",
                "{\"draftId\":\"12345678-1234-1234-1234-123456789012\",\"confirmed\":false}")) {
            mvc.perform(post("/student/bookingAssistant/confirm").contentType("application/json").content(body))
                    .andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        }
        verifyNoInteractions(confirmation);
    }

    @Test
    void otherRolesCannotPrepareOrConfirmEvenWithInheritedStudentPermission() throws Exception {
        for (String role : List.of("PS", "Admin", "SuAdmin")) {
            var user = new CurrentUser(); user.setRoleKey(role); CurrentUserUtils.setUser(user);
            mvc.perform(post("/student/bookingAssistant/confirm").contentType("application/json")
                            .content("{\"draftId\":\"12345678-1234-1234-1234-123456789012\",\"confirmed\":true}"))
                    .andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
            mvc.perform(post("/student/bookingAssistant/prepare").contentType("application/json")
                            .content("{\"assessmentSlotId\":100,\"slotVersion\":0,\"startTime\":\"2026-10-27T14:00:00\",\"endTime\":\"2026-10-27T14:30:00\"}"))
                    .andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
        }
        verifyNoInteractions(confirmation);
    }
}
