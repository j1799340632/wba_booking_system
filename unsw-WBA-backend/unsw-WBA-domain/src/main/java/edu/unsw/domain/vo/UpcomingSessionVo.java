package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/6/2026 14:18
 * @description UpcomingSessionVo
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "Upcoming session view object to return to the front end")
public class UpcomingSessionVo extends AssessmentSlotVo {
    @Schema(description = "Booking id")
    private Long bookingId;

    @Schema(description = "Student id")
    private Long studentId;

    @Schema(description = "Student name")
    private String studentName;
}
