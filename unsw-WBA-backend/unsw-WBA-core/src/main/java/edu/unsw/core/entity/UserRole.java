package edu.unsw.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 23/6/2026 19:01
 * @description user role entity
 */
@Data
@TableName(value = "user_role", autoResultMap = true)
@Schema(title = "User Role Entity")
public class UserRole implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Role permission priority, the lower, the higher the permission")
    private Integer permissionPriority;

    @Schema(description = "User real name")
    @Length(min = 1, max = 50, message = "Role key cannot be longer than 50 characters and cannot be empty")
    private String roleKey;

    @Schema(description = "User real name")
    @Length(min = 1, max = 50, message = "Role name cannot be longer than 50 characters and cannot be empty")
    private String roleName;

    @Schema(description = "1/true is enabled")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isEnable;

    @Schema(description = "1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "Creator id")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Create time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;
}
