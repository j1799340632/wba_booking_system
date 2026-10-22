package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 29/7/2026 09:28
 * @description Base query field
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "User role Query field")
public class UserRoleQueryDto extends BaseQueryDto {
    @Schema(description = "keyword")
    private String keyword;
}
