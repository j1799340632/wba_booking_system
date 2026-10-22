package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:15
 * @description AssessmentSlot entity
 */
@Data
@TableName(value = "assessment_slot", autoResultMap = true)
@Schema(title = "AssessmentSlot entity")
public class AssessmentSlot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Available start time")
    @NotNull(message = "Start time cannot be null")
    private LocalDateTime startTime;

    @Schema(description = "Available end time")
    @NotNull(message = "End time cannot be null")
    private LocalDateTime endTime;

    @Schema(description = "0: no repeat, 1 daily, 2 weekly, 3 monthly, 4 yearly")
    private Integer repeatType;

    @Schema(description = "Repeat start time")
    private LocalDateTime repeatStartTime;

    @Schema(description = "Repeat end time")
    private LocalDateTime repeatEndTime;

    @Schema(description = "Most recent available time")
    private LocalDateTime nextAvailableTime;

    @Schema(description = "Rrule i-Calendar (RFC 5545)")
    private String rrule;

    @Schema(description = "Assess hospital")
    @NotBlank(message = "Hospital cannot be empty")
    @Length(max = 255, message = "Hospital cannot be longer than 255 characters")
    private String hospital;

    @Schema(description = "Assess department")
    @NotBlank(message = "Department cannot be empty")
    @Length(max = 255, message = "Department cannot be longer than 255 characters")
    private String department;

    @Schema(description = "Assess location")
    @NotBlank(message = "Location cannot be empty")
    @Length(max = 255, message = "Location cannot be longer than 255 characters")
    private String location;

    @Schema(description = "WBA types offered")
    private String type;

    @Schema(description = "Slot status, 0 available, 1 booked, 2 completed")
    @TableField(fill = FieldFill.INSERT)
    private Integer status;

    @Schema(description = "Creator id")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Create time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;

    @Schema(description = "Is available for student query")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isEnable;

    @Schema(description = "1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "Optimistic lock version")
    @TableField(fill = FieldFill.INSERT)
    @Version
    private Integer version;
}
