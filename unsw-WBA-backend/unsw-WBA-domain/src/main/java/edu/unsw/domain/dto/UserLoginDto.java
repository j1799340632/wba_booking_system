package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 20:09
 * @description User login data transfer object from the front end
 */
@Data
@Schema(title = "User login data transfer object from the front end")
public class UserLoginDto {
    @Schema(description = "User email")
    @Email(message = "Email format is incorrect")
    private String email;

    @Schema(description = "Login password")
    @NotBlank(message = "Password cannot be empty")
    private String password;
}
