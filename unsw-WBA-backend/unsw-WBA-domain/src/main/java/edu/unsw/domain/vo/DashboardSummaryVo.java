package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:55
 * @description Registrar dashboard summary cards
 */
@Data
@Schema(title = "Registrar dashboard summary")
public class DashboardSummaryVo {
    @Schema(description = "Number of completed WBAs")
    private Long completedWbas;

    @Schema(description = "Total number of bookings")
    private Long totalBookings;

    @Schema(description = "Number of available slots")
    private Long availableSlots;

    @Schema(description = "Number of active supervisors")
    private Long activeSupervisors;
}
