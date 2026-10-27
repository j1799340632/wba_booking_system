package edu.unsw.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
public class AssessmentPlanSaveDto {
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate deadline;
    @NotNull @Min(0) private Integer expectedVersion;
    @NotEmpty @Size(max=20) private List<@NotNull @Valid Goal> goals;

    @Getter @Setter
    public static class Goal {
        @NotNull @Positive private Long typeId;
        @NotNull @Min(1) @Max(100) private Integer target;
    }
}
