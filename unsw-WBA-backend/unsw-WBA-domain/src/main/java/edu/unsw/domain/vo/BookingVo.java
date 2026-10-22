package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author WBA Connect
 * @date 24/6/2026 21:22
 * @description Booking view object
 */
@Data
@Schema(title = "Booking view object")
public class BookingVo {
    @Schema(description = "Booking id")
    private Long bookingId;

    @Schema(description = "Availability slot id")
    private Long slotId;

    @Schema(description = "Supervisor id")
    private Long supervisorId;

    @Schema(description = "Supervisor name")
    private String supervisorName;

    @Schema(description = "Supervisor title")
    private String supervisorTitle;

    @Schema(description = "Selected WBA type")
    private String wbaType;

    @Schema(description = "Hospital")
    private String hospital;

    @Schema(description = "Department")
    private String department;

    @Schema(description = "Location")
    private String location;

    @Schema(description = "Start time")
    private LocalDateTime startTime;

    @Schema(description = "End time")
    private LocalDateTime endTime;

    @Schema(description = "Duration minutes")
    private String durationMinutes;

    @Schema(description = "Booking status, 0: Pending, 1: Confirmed, 2: Completed, 3: Declined, 4: Canceled")
    private Integer status;

    @Schema(description = "Optimistic lock version")
    private Integer version;
}
