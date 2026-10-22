package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author WBA Connect
 * @date 19/7/2026
 * @description Registrar supervisor view object
 */
@Data
@Schema(title = "Registrar supervisor view object")
public class RegistrarSupervisorVo {
    @Schema(description = "Supervisor user id")
    private Long id;

    @Schema(description = "Supervisor name")
    private String name;

    @Schema(description = "Supervisor title")
    private String title;

    @Schema(description = "Supervisor email")
    private String email;

    @Schema(description = "Supervisor hospital")
    private String hospital;

    @Schema(description = "Supervisor department")
    private String department;

    @Schema(description = "Supervisor location")
    private String location;

    @Schema(description = "Supervisor preferred WBA types")
    private String wbaTypes;

    @Schema(description = "Number of completed assessments")
    private Long completedAssessments;

    @Schema(description = "Supervisor account status, true when enabled")
    private Boolean status;
}
