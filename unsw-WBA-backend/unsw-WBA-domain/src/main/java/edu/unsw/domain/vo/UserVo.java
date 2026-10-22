package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 23/6/2026 18:05
 * @description User view object to return to the front end
 */
@Data
@Schema(title = "User view object to return to the front end")
public class UserVo {
    @Schema(description = "User token")
    private String token;

    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "User avatar")
    private String avatar;

    @Schema(description = "User real name")
    private String name;

    @Schema(description = "User nickname")
    private String nickname;

    @Schema(description = "User title")
    private String title;

    @Schema(description = "User gender")
    private String gender;

    @Schema(description = "User age")
    private Integer age;

    @Schema(description = "UNSW zId")
    private String zId;

    @Schema(description = "UNSW faculty")
    private String faculty;

    @Schema(description = "UNSW grade")
    private String grade;

    @Schema(description = "Assess hospital")
    private String hospital;

    @Schema(description = "Assess department")
    private String department;

    @Schema(description = "Assess location")
    private String location;

    @Schema(description = "Preferred assess type")
    private String preferredAssessType;

    @Schema(description = "Preferred assess student level")
    private String preferredStudentLevel;

    @Schema(description = "Is available to receive booking")
    private Boolean isAvailable;

    @Schema(description = "Available days")
    private String availableDays;

    @Schema(description = "Available start time")
    private LocalTime availableStartTime;

    @Schema(description = "Available end time")
    private LocalTime availableEndTime;

    @Schema(description = "User email(Login)")
    private String email;

    @Schema(description = "User cancellation status, 1/true is canceled")
    private Boolean isCanceled;

    @Schema(description = "User account state, 1/true is enabled")
    private Boolean isEnable;

    @Schema(description = "User account status, 1/true is deleted")
    private Boolean isDelete;

    @Schema(description = "Last login time")
    private LocalDateTime lastLoginTime;

    @Schema(description = "Creator id, if null, it indicates the user is registered by the user self")
    private Long creator;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    private LocalDateTime editTime;

    @Schema(description = "User role id")
    private Long roleId;

    @Schema(description = "Role permission priority, the lower, the higher the permission")
    private Integer permissionPriority;

    @Schema(description = "User role key")
    private String roleKey;

    @Schema(description = "User role name")
    private String roleName;
}
