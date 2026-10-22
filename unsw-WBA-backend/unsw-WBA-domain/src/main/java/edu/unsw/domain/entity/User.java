package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 19:52
 * @description user entity
 */
@Data
@TableName(value = "user", autoResultMap = true)
@Schema(title = "User Entity")
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "User role id")
    private Long roleId;

    @Schema(description = "User avatar")
    private String avatar;

    @Schema(description = "User real name")
    @Length(max = 50, message = "Name cannot be longer than 50 characters")
    private String name;

    @Schema(description = "User nickname")
    @Length(max = 50, message = "Nickname cannot be longer than 50 characters")
    private String nickname;

    @Schema(description = "User title")
    @Length(max = 50, message = "Title cannot be longer than 50 characters")
    private String title;

    @Schema(description = "User gender")
    private String gender;

    @Schema(description = "User age")
    @Range(min = 0, max = 150, message = "You are not a god, please enter a valid age")
    private Integer age;

    @Schema(description = "UNSW zId")
    private String zId;

    @Schema(description = "UNSW faculty")
    private String faculty;

    @Schema(description = "UNSW grade")
    private String grade;

    @Schema(description = "Assess hospital")
    @Length(max = 255, message = "Hospital cannot be longer than 255 characters")
    private String hospital;

    @Schema(description = "Assess department")
    @Length(max = 255, message = "Department cannot be longer than 255 characters")
    private String department;

    @Schema(description = "Assess location")
    @Length(max = 255, message = "Location cannot be longer than 255 characters")
    private String location;

    @Schema(description = "Preferred assess type")
    @Length(max = 255, message = "Preferred assess type cannot be longer than 255 characters")
    private String preferredAssessType;

    @Schema(description = "Preferred assess student level")
    @Length(max = 255, message = "Preferred assess student level cannot be longer than 255 characters")
    private String preferredStudentLevel;

    @Schema(description = "Available days")
    @Length(max = 255, message = "Available days cannot be longer than 255 characters")
    private String availableDays;

    @Schema(description = "Is available to receive booking")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isAvailable;

    @Schema(description = "Available start time")
    private LocalTime availableStartTime;

    @Schema(description = "Available end time")
    private LocalTime availableEndTime;

    @Schema(description = "User email(Login)")
    @Email(message = "Email format is incorrect")
    private String email;

    /**
     * Ignore this field when sending data to the front end
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Login password")
    @Length(max = 255, message = "Password cannot be longer than 255 characters")
    private String password;

    @Schema(description = "User cancellation status, 1/true is canceled")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isCanceled;

    @Schema(description = "1/true is enabled")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isEnable;

    @Schema(description = "1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "Last login time")
    private LocalDateTime lastLoginTime;

    @Schema(description = "Creator id, if null, it indicates the user is registered by the user self")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Create time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;
}
