package edu.unsw.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Deliberately has no content-bearing toString: controller debug logs must not log chat text. */
@Getter
@Setter
public class BookingAssistantRequest {
    @NotBlank(message = "Please describe the sessions you want to find")
    @Size(max = 1500, message = "Please keep your request within 1500 characters")
    private String message;

    @Size(max = 6, message = "Please start a new search after six follow-up requests")
    private List<@NotBlank @Size(max = 1500) String> history = List.of();
    /** Explicit UI selection, never supplied by the model. */
    @Positive private Long rescheduleBookingId;
}
