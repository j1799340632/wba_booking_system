package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:19
 * @description AssessmentBooking entity
 */
@Data
@TableName(value = "assessment_booking", autoResultMap = true)
@Schema(title = "AssessmentBooking entity")
public class AssessmentBooking implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Booking start time")
    private LocalDateTime startTime;

    @Schema(description = "Booking end time")
    private LocalDateTime endTime;

    @Schema(description = "Assessment slot id")
    private Long assessmentSlotId;

    @Schema(description = "Booking status, 0: Pending, 1: Confirmed, 2: Completed, 3: Declined, 4: Canceled")
    @TableField(fill = FieldFill.INSERT)
    private Integer status;

    @Schema(description = "When booking status is completed, the assess score")
    private Double score;

    @Schema(description = "Assessment feedback")
    private String feedback;

    @Schema(description = "Assessment completed time")
    private LocalDateTime completedTime;

    @Schema(description = "Creator id")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Create time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;

    @Schema(description = "1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "Optimistic lock version")
    @TableField(fill = FieldFill.INSERT)
    @Version
    private Integer version;
}
