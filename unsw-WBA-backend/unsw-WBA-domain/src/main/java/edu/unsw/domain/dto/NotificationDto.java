package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 13/7/2026 15:19
 * @description Notification data transfer object from the front end
 */
@Data
@Schema(title = "Notification data transfer object from the front end")
public class NotificationDto {
    @Schema(defaultValue = "Message id")
    private Long messageId;

    @Schema(description = "Send notification user id, if 0, means system")
    private Long senderId;

    @Schema(description = "To be notified user id")
    private Long receiverId;

    @Schema(description = "Notification name")
    private String eventName;

    @Schema(description = "Notification content")
    private String eventContent;

    @Schema(description = "Whether send email simultaneously")
    private Boolean isSendEmail = true;
}
