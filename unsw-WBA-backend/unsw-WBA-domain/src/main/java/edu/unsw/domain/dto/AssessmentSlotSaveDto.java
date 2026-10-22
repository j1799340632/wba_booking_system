package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 19/7/2026 21:23
 * @description Save AssessmentSlot data transfer object
 */
@Data
@Schema(title = "Save AssessmentSlot data transfer object")
public class AssessmentSlotSaveDto {
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Available start time")
    @NotNull(message = "Start time cannot be null")
    private LocalDateTime startTime;

    @Schema(description = "Available end time")
    @NotNull(message = "End time cannot be null")
    private LocalDateTime endTime;

    @Schema(description = "Repeat start time")
    private LocalDateTime repeatStartTime;

    @Schema(description = "Repeat end time")
    private LocalDateTime repeatEndTime;

    @Schema(description = "0: no repeat, 1 daily, 2 weekly, 3 monthly, 4 yearly")
    private Integer repeatType;

    @Schema(description = "Interval daily/weeks/...; 1 represents daily/weekly/..., 2 represents bi-daily/bi-weekly/...")
    private Integer interval;

    @Schema(description = "Specific abbreviations for days of the week (MO, TU, WE, TH, FR, SA, SU)  only for weekly repeat type")
    private List<String> daysOfWeek;

    @Schema(description = "Specific numbers (such as [1, 15]) of date of the month for monthly and yearly repeat type")
    private List<Integer> daysOfMonth;

    @Schema(description = "The specified list of months (1-12) only for yearly repeat type")
    private List<Integer> months;

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

    @Schema(description = "Optimistic lock version")
    private Integer version;
}
