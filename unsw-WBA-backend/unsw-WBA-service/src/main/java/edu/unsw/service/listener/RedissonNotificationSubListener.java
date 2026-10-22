package edu.unsw.service.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.service.sse.SseSessionManager;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.Message;
import edu.unsw.service.MessageService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 13/7/2026 14:52
 * @description Redisson Subscription Listener
 */
@Component
public class RedissonNotificationSubListener {
    private static final Logger logger = LoggerFactory.getLogger(RedissonNotificationSubListener.class);

    private Integer listenerId = null;

    private final SseSessionManager sseSessionManager;
    private final RedissonUtils redissonUtils;
    private final MessageService messageService;

    public RedissonNotificationSubListener(SseSessionManager sseSessionManager, RedissonUtils redissonUtils, MessageService messageService) {
        this.sseSessionManager = sseSessionManager;
        this.redissonUtils = redissonUtils;
        this.messageService = messageService;
    }

    @PostConstruct
    public void init() {
        listenerId = redissonUtils.subscribe(
                RedisKeyConstant.SSE_NOTIFY_CHANNEL,
                NotificationDto.class,
                this::handleNotice
        );

        logger.info("Start Redisson Listener!");
    }

    /**
     * Business logic after receiving a message (implementation of Consumer)
     */
    private void handleNotice(NotificationDto notificationDto) {
        logger.info("Receive Message: {}", notificationDto);

        try {
            SseEmitter emitter = sseSessionManager.get(String.valueOf(notificationDto.getReceiverId()));
            if (emitter != null) {
                emitter.send(
                        SseEmitter
                                .event()
                                .name(notificationDto.getEventName())
                                .data(notificationDto.getEventContent())
                );

                messageService.update(
                        Wrappers.<Message>lambdaUpdate()
                                .set(Message::getIsDelivery, true)
                                .set(Message::getDeliveryTime, LocalDateTime.now(ZoneId.systemDefault()))
                                .eq(Message::getId, notificationDto.getMessageId())
                );
            }
        } catch (Exception e) {
            logger.warn("SSE push failed: {}",notificationDto, e);
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info("Stopping Redisson Listener...");
        redissonUtils.unsubscribe(RedisKeyConstant.SSE_NOTIFY_CHANNEL, listenerId);
    }
}
