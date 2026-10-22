package edu.unsw.domain.vo;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 29/7/2026 09:22
 * @description user role entity
 */
@Data
@Schema(title = "User Role Vo")
public class UserRoleVo {
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Role permission priority, the lower, the higher the permission")
    private Integer permissionPriority;

    @Schema(description = "User real name")
    private String roleKey;

    @Schema(description = "User real name")
    private String roleName;

    @Schema(description = "1/true is enabled")
    private Boolean isEnable;

    @Schema(description = "1/true is deleted")
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "Creator id")
    private Long creator;

    @Schema(description = "Creator name")
    private String creatorName;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    private LocalDateTime editTime;
}
