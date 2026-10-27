package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(title = "Assessment completion request")
public class AssessmentCompletionDto {

    @NotNull(message = "Booking id required")
    @Schema(description = "Booking id")
    private Long bookingId;

    @NotNull(message = "Score required")
    @DecimalMin(value = "0.0", message = "Score cannot be negative")
    @DecimalMax(value = "100.0", message = "Score cannot exceed 100")
    @Schema(description = "Assessment score")
    private Double score;

    @NotBlank(message = "Feedback required")
    @Size(max = 255, message = "Feedback cannot exceed 255 characters")
    @Schema(description = "Assessment feedback")
    private String feedback;

    @Schema(description = "Optimistic lock version")
    @NotNull(message = "Booking version required")
    @Min(value = 0, message = "Booking version cannot be negative")
    private Integer version;
}
