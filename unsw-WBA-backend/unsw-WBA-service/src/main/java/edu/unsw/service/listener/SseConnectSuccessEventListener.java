package edu.unsw.service.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.dao.mapper.MessageMapper;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.Message;
import edu.unsw.service.SseService;
import edu.unsw.service.constant.SseEventConstant;
import edu.unsw.service.event.SseConnectSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/7/16 14:16
 * @description SseConnectSuccessEventListener
 */
@Component
public class SseConnectSuccessEventListener {
    private final Logger logger = LoggerFactory.getLogger(SseConnectSuccessEventListener.class);

    private final MessageMapper messageMapper;
    private final SseService sseService;

    public SseConnectSuccessEventListener(MessageMapper messageMapper, SseService sseService) {
        this.messageMapper = messageMapper;
        this.sseService = sseService;
    }

    @EventListener
    @Async
    public void onEvent(SseConnectSuccessEvent sseConnectSuccessEvent) {
        logger.info("SSE connect success event detected: {}", sseConnectSuccessEvent);
        Long userId = (Long) sseConnectSuccessEvent.getSource();
        // Notify users of unread messages
        List<Message> unreadMessageList  = messageMapper.selectList(
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getReceiver, userId)
                        .eq(Message::getIsDelivery, false)
        );
        if (!unreadMessageList.isEmpty()) {
            NotificationDto notificationDto = new NotificationDto();
            notificationDto.setEventName(SseEventConstant.NOTIFICATION);
            notificationDto.setEventContent("You have received " + unreadMessageList.size() + " messages during offline, check the message center");
            notificationDto.setReceiverId(userId);
            notificationDto.setSenderId(0L);
            sseService.sendNotification(notificationDto);

            // Update this message to delivered
            unreadMessageList.forEach(item -> {
                item.setDeliveryTime(LocalDateTime.now(ZoneId.systemDefault()));
                item.setIsDelivery(true);
            });
            messageMapper.updateById(unreadMessageList);
        }
    }
}
