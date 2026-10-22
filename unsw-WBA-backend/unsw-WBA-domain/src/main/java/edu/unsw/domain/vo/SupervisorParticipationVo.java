package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:55
 * @description Registrar dashboard supervisor participation
 */
@Data
@Schema(title = "Registrar dashboard supervisor participation")
public class SupervisorParticipationVo {
    @Schema(description = "Supervisor id")
    private Long supervisorId;

    @Schema(description = "Supervisor name")
    private String supervisorName;

    @Schema(description = "Number of bookings")
    private Long count;
}
