package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(title = "Completed assessment history")
public class AssessmentHistoryVo extends BookingVo {
    @Schema(description = "Assessment score")
    private Double score;

    @Schema(description = "Assessment feedback")
    private String feedback;

    @Schema(description = "Assessment completed time")
    private LocalDateTime completedTime;

    @Schema(description = "Student id")
    private Long studentId;

    @Schema(description = "Student name")
    private String studentName;
}
