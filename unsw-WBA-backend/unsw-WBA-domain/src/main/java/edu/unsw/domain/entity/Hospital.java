package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:14
 * @description Hospital entity
 */
@Data
@TableName(value = "hospital", autoResultMap = true)
@Schema(title = "Hospital entity")
public class Hospital implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Hospital name")
    private String name;

    @Schema(description = "Creator id")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Create time")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;

    @Schema(description = "1/true is enabled")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isEnable;

    @Schema(description = "Role status, 1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;
}
