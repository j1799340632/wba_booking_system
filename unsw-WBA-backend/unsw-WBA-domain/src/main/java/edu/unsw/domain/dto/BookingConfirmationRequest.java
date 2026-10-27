package edu.unsw.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingConfirmationRequest {
    @NotBlank @Pattern(regexp = "[a-f0-9-]{36}")
    private String draftId;
    @NotNull @AssertTrue(message = "Review the session and confirm before booking")
    private Boolean confirmed;
}
