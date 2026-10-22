package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:55
 * @description Registrar dashboard WBA type breakdown
 */
@Data
@Schema(title = "Registrar dashboard WBA type breakdown")
public class WbaTypeBreakdownVo {
    @Schema(description = "WBA type")
    private String type;

    @Schema(description = "Booking count for this type")
    private Long count;

    @Schema(description = "Percentage of total bookings")
    private Double percentage;
}
