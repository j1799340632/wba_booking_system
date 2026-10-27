package edu.unsw.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingManagementRequest {
    @NotNull @Positive private Long bookingId;
    @NotNull @PositiveOrZero private Integer bookingVersion;
    @NotNull private Action action;
    @Valid private AssessmentBookingSaveDto replacement;
    public enum Action { CANCEL, RESCHEDULE }
}
