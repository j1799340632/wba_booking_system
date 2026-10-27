package edu.unsw.domain.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class BookingReplacementQueryDto {
    @NotNull @Positive private Long bookingId;
    @NotNull @Valid private AssessmentSlotQueryDto conditions;
}
