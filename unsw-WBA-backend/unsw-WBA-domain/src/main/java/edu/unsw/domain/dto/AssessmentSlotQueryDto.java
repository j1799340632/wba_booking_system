package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/6/2026 16:37
 * @description AssessmentSlotQueryDto
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "AssessmentSlot Query field")
public class AssessmentSlotQueryDto extends BaseQueryDto {
    @Schema(description = "supervisorId")
    private Long supervisorId;

    @Schema(description = "Supervisor name substring; combined with supervisorId when both are supplied")
    private String supervisorName;

    @Schema(description = "Hospital fuzzy query")
    private String hospital;

    @Schema(description = "Department fuzzy query")
    private String department;

    @Schema(description = "WBA type fuzzy query")
    private String type;

    @Schema(description = "Slot status, reference edu.unsw.service.enums.AssessmentSlotStatusEnum")
    private Integer status;

    @Schema(description = "Sort by most recent available time")
    private Boolean isOrderByAvailableTime;
}
