package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 18:35
 * @description user entity
 */
@Data
@TableName(value = "user_settings", autoResultMap = true)
@Schema(title = "User Settings Entity")
public class UserSettings implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "User id")
    private Long userId;

    @Schema(description = "1/true is agree receive email notification")
    private Boolean isReceiveEmail;

    @Schema(description = "1/true is play sound")
    private Boolean isMessageSound;
}
