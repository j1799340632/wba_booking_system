package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author WBA Connect
 * @date 19/7/2026
 * @description Registrar supervisor query fields
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "Registrar supervisor query fields")
public class RegistrarSupervisorQueryDto extends BaseQueryDto {
    @Schema(description = "Search keyword for supervisor name")
    private String keyword;

    @Schema(description = "Supervisor hospital")
    private String hospital;

    @Schema(description = "Supervisor department")
    private String department;
}
