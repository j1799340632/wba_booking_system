package edu.unsw.api.controller.registrar;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.vo.RegistrarDashboardVo;
import edu.unsw.service.RegistrarDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:56
 * @description Registrar dashboard-related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.REGISTRAR_PREFIX + "/dashboard")
@Validated
@Tag(name = "Registrar dashboard interfaces")
public class RegistrarDashboardController {
    private final RegistrarDashboardService registrarDashboardService;

    public RegistrarDashboardController(RegistrarDashboardService registrarDashboardService) {
        this.registrarDashboardService = registrarDashboardService;
    }

    @GetMapping("/getDashboard")
    @Operation(summary = "Get registrar dashboard")
    public ResultEntity<RegistrarDashboardVo> getDashboard(@RequestParam(required = false) String hospital,
                                                           @RequestParam(required = false) String department,
                                                           @RequestParam(required = false) Long supervisorId) {
        return ResultEntity.success("Get registrar dashboard successfully",
                registrarDashboardService.getDashboard(hospital, department, supervisorId));
    }
}
