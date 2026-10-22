package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:55
 * @description Registrar dashboard aggregate response
 */
@Data
@Schema(title = "Registrar dashboard")
public class RegistrarDashboardVo {
    @Schema(description = "Summary cards")
    private DashboardSummaryVo summary;

    @Schema(description = "Weekly activity chart")
    private List<WeeklyActivityVo> weeklyActivity;

    @Schema(description = "WBA type breakdown")
    private List<WbaTypeBreakdownVo> wbaTypeBreakdown;

    @Schema(description = "Supervisor participation")
    private List<SupervisorParticipationVo> supervisorParticipation;
}
