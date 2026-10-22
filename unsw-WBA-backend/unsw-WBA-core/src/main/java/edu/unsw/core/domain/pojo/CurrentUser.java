package edu.unsw.core.domain.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 18:27
 * @description The currently requested user
 */
@Data
@Schema(title = "The currently requested user")
public class CurrentUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "User token")
    private String token;

    @Schema(description = "Primary key")
    private Long id;

    @Schema(description = "User email")
    private String email;

    @Schema(description = "User real name")
    private String name;

    @Schema(description = "User role id")
    private Long roleId;

    @Schema(description = "User role key")
    private String roleKey;
}
