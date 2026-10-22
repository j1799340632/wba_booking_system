package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:38
 * @description Message Entity
 */
@Data
@TableName(value = "message", autoResultMap = true)
@Schema(title = "Message Entity")
public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Message receiver")
    private Long receiver;

    @Schema(description = "Message sender, if 0, means system")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "Is read by receiver")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isRead;

    @Schema(description = "Read time")
    private LocalDateTime readTime;

    @Schema(description = "Is delivered to receiver")
    @TableField(fill = FieldFill.INSERT)
    private Boolean isDelivery;

    @Schema(description = "Delivery time")
    private LocalDateTime deliveryTime;

    @Schema(description = "Create time(Send time)")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime editTime;

    @Schema(description = "1/true is deleted")
    @TableField(fill = FieldFill.INSERT)
    @TableLogic
    private Boolean isDelete;
}
