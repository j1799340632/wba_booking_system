package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:49
 * @description Message view object
 */
@Data
public class MessageVo {
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Message receiver id")
    private Long receiver;

    @Schema(description = "Message sender id, if 0, means system")
    private Long creator;

    @Schema(description = "Creator name")
    private String creatorName;

    @Schema(description = "Creator avatar url")
    private String creatorAvatar;

    @Schema(description = "Is read by receiver")
    private Boolean isRead;

    @Schema(description = "Read time")
    private LocalDateTime readTime;

    @Schema(description = "Is delivered to receiver")
    private Boolean isDelivery;

    @Schema(description = "Delivery time")
    private LocalDateTime deliveryTime;

    @Schema(description = "Create time(Send time)")
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    private LocalDateTime editTime;

    @Schema(description = "1/true is deleted")
    private Boolean isDelete;
}
