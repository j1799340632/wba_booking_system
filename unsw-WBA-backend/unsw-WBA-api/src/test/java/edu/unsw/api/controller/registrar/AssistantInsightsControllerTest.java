package edu.unsw.api.controller.registrar;

import edu.unsw.core.config.exception.GlobalExceptionHandler;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.vo.AssistantInsightsVo;
import edu.unsw.service.AssistantInsightsService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssistantInsightsControllerTest {
    AssistantInsightsService service;MockMvc mvc;
    @BeforeEach void setup(){service=mock(AssistantInsightsService.class);mvc=MockMvcBuilders.standaloneSetup(new AssistantInsightsController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();}
    @AfterEach void cleanup(){CurrentUserUtils.remove();}
    private void actor(String role){var actor=new CurrentUser();actor.setId(2L);actor.setRoleKey(role);CurrentUserUtils.setUser(actor);}
    @Test void adminsCanLoadDefaultOrExplicitDates() throws Exception {
        when(service.get(any(),any())).thenReturn(new AssistantInsightsVo());actor("Admin");
        mvc.perform(get("/registrar/assistantInsights")).andExpect(jsonPath("$.code").value(2000));
        actor("SuAdmin");mvc.perform(get("/registrar/assistantInsights").param("startDate","2026-10-20").param("endDate","2026-10-26")).andExpect(jsonPath("$.code").value(2000));
        verify(service).get(null,null);verify(service).get(LocalDate.of(2026,10,20),LocalDate.of(2026,10,26));
    }
    @Test void studentsSupervisorsAndMissingIdentityCannotReadObservations() throws Exception {
        for(String role:java.util.List.of("Stu","PS")){actor(role);mvc.perform(get("/registrar/assistantInsights")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));}
        CurrentUserUtils.remove();mvc.perform(get("/registrar/assistantInsights")).andExpect(jsonPath("$.code").value(ResponseEnum.NO_PERMISSION.getCode()));verifyNoInteractions(service);
    }
}
