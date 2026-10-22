package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 17:18
 * @description AssessmentBooking save dto
 */
@Data
@Schema(title = "AssessmentBooking save dto")
public class AssessmentBookingSaveDto {
    @Schema(description = "Booking start time")
    @NotNull(message = "Start time cannot be null")
    private LocalDateTime startTime;

    @Schema(description = "Booking end time")
    @NotNull(message = "End time cannot be null")
    private LocalDateTime endTime;

    @Schema(description = "Assessment slot id")
    @NotNull(message = "Assessment id cannot be null")
    private Long assessmentSlotId;

    @Schema(description = "Slot version, used to check if the slot has been updated")
    private Integer slotVersion;
}
