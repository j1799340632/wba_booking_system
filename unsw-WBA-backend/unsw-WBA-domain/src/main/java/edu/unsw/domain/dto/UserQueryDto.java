package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 20/7/2026
 * @description Registrar query user list
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "Registrar query user list")
public class UserQueryDto extends BaseQueryDto {
    @Schema(description = "User real name")
    private String name;

    @Schema(description = "Hospital")
    private String hospital;

    @Schema(description = "Department")
    private String department;
}