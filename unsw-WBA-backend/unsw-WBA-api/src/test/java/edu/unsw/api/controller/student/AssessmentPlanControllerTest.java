package edu.unsw.api.controller.student;

import edu.unsw.core.config.exception.GlobalExceptionHandler;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.vo.AssessmentPlanVo;
import edu.unsw.service.AssessmentPlanningService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssessmentPlanControllerTest {
    AssessmentPlanningService planning;
    MockMvc mvc;
    static final String BODY="{\"startDate\":\"2026-10-26\",\"deadline\":\"2026-11-23\",\"expectedVersion\":0,\"goals\":[{\"typeId\":100,\"target\":3}]}";
    @BeforeEach void setup() {
        planning=mock(AssessmentPlanningService.class);
        mvc=MockMvcBuilders.standaloneSetup(new AssessmentPlanController(planning)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { CurrentUserUtils.remove(); }
    @Test void studentCanLoadFullOrSummaryAndSaveTargets() throws Exception {
        actor("Stu"); when(planning.get()).thenReturn(new AssessmentPlanVo()); when(planning.summary()).thenReturn(new AssessmentPlanVo()); when(planning.save(any())).thenReturn(1);
        mvc.perform(get("/student/assessmentPlan")).andExpect(jsonPath("$.code").value(2000));
        mvc.perform(get("/student/assessmentPlan?summary=true")).andExpect(jsonPath("$.code").value(2000));
        mvc.perform(post("/student/assessmentPlan").contentType("application/json").content(BODY)).andExpect(jsonPath("$.data").value(1));
        verify(planning).get(); verify(planning).summary(); verify(planning).save(any());
    }
    @Test void otherRolesCannotUseInheritedStudentPermissionsToReadOrSavePlan() throws Exception {
        for(String role:List.of("PS","Admin","SuAdmin")) {
            actor(role);
            mvc.perform(get("/student/assessmentPlan")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
            mvc.perform(post("/student/assessmentPlan").contentType("application/json").content(BODY)).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));
        }
        verifyNoInteractions(planning);
    }
    @Test void malformedOrInvalidTargetsAreRejectedBeforeService() throws Exception {
        actor("Stu");
        for(String body:List.of("{}",BODY.replace("\"target\":3","\"target\":0"),BODY.replace("\"expectedVersion\":0","\"expectedVersion\":-1"),BODY.replace("\"typeId\":100","\"typeId\":null")))
            mvc.perform(post("/student/assessmentPlan").contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(ResponseEnum.ILLEGAL_PARAM.getCode()));
        verifyNoInteractions(planning);
    }
    private void actor(String role) { var user=new CurrentUser(); user.setId(1L); user.setRoleKey(role); CurrentUserUtils.setUser(user); }
}
