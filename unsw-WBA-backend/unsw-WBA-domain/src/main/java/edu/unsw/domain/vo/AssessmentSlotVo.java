package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:32
 * @description Assessment slot view object to return to the front end
 */
@Data
@Schema(title = "Assessment slot view object to return to the front end")
public class AssessmentSlotVo {
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Repeat start time")
    private LocalDateTime repeatStartTime;

    @Schema(description = "Repeat end time")
    private LocalDateTime repeatEndTime;

    @Schema(description = "Most recent available time")
    private LocalDateTime nextAvailableTime;

    @Schema(description = "Available time list")
    private List<List<LocalDateTime>> availableTimeList;

    @Schema(description = "Business time zone used by student availability search")
    private String timeZone;

    @Schema(description = "Available start time")
    private LocalDateTime startTime;

    @Schema(description = "Available end time")
    private LocalDateTime endTime;

    @Schema(description = "Available time period")
    private String duration;

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

    @Schema(description = "Rrule i-Calendar (RFC 5545)")
    private String rrule;

    @Schema(description = "Assess department")
    private String department;

    @Schema(description = "Assess hospital")
    private String hospital;

    @Schema(description = "Assess location")
    private String location;

    @Schema(description = "WBA types offered")
    private String type;

    @Schema(description = "Slot status, 0 available, 1 has been booked, 2 is completed")
    private Integer status;

    @Schema(description = "Creator id")
    private Long creator;

    @Schema(description = "Creator real name")
    private String creatorName;

    @Schema(description = "Creator avatar")
    private String creatorAvatar;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    private LocalDateTime editTime;

    @Schema(description = "1/true is enabled")
    private Boolean isEnable;

    @Schema(description = "1/true is deleted")
    private Boolean isDelete;

    @Schema(description = "Optimistic lock version")
    private Integer version;
}
