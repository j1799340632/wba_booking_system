package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.util.List;

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
    private Long preferredSupervisorId;
    private String preferredSupervisorName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Schema(description = "Earliest daily session start in the business time zone")
    private LocalTime earliestTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Schema(description = "Latest daily session end in the business time zone")
    private LocalTime latestTime;

    @Schema(description = "ISO weekdays, Monday=1 through Sunday=7; empty means any day")
    private List<Integer> weekdays = List.of();

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
