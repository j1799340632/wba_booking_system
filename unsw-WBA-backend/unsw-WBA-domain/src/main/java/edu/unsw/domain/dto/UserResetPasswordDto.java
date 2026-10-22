package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/7/2026 20:09
 * @description User reset password data transfer object from the front end
 */
@Data
@Schema(title = "User reset password data transfer object from the front end")
public class UserResetPasswordDto {
    @Schema(description = "Target user id")
    @NotNull(message = "User id cannot be empty")
    private Long targetUserId;

    @Schema(description = "New password")
    @NotBlank(message = "New password cannot be empty")
    private String newPassword;

    @Schema(description = "Verify code")
    @NotNull(message = "Verify code cannot be empty")
    private Integer verifyCode;
}
