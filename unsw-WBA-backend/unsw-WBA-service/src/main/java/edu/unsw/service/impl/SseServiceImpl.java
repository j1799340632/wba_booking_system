package edu.unsw.service.impl;

import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.service.sse.SseSessionManager;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.service.SseService;
import edu.unsw.service.event.SseConnectSuccessEvent;
import edu.unsw.service.event.MyEventPublish;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 14/7/2026 21:59
 * @description SSE service implementation
 */
@Service
public class SseServiceImpl implements SseService {
    private final Logger logger = LoggerFactory.getLogger(SseServiceImpl.class);
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private final RedissonUtils redissonUtils;
    private final SseSessionManager sseSessionManager;
    private final MyEventPublish myEventPublish;

    public SseServiceImpl(RedissonUtils redissonUtils, SseSessionManager sseSessionManager, MyEventPublish myEventPublish) {
        this.redissonUtils = redissonUtils;
        this.sseSessionManager = sseSessionManager;
        this.myEventPublish = myEventPublish;
    }

    @Override
    public String getTicket() {
        String ticket = "SSE_TK_" + UUID.randomUUID().toString().replace("-", "");

        String redisKey = RedisKeyConstant.SSE_TICKET_PREFIX + CurrentUserUtils.getUser().getId();
        redissonUtils.setObject(redisKey, ticket, 3L);
        return ticket;
    }

    @Override
    public SseEmitter subscribe(String userId, String ticket, HttpServletResponse response) {
        if (Objects.isNull(userId) || Objects.isNull(ticket)) {
            return null;
        }

        String redisKey = RedisKeyConstant.SSE_TICKET_PREFIX + userId;
        String sseTicket = redissonUtils.getObject(redisKey);
        if (Objects.isNull(sseTicket) || !Objects.equals(sseTicket, ticket)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

            SseEmitter badEmitter = new SseEmitter(0L);
            badEmitter.complete();
            return badEmitter;
        }

        // Set the timeout period; 0 means never timeout (for production environments, it is recommended to set it to 30-60 minutes, relying on front-end reconnection after disconnection)
        SseEmitter emitter = new SseEmitter(0L);

        // Binding callback: Remove the user from the Map when the connection is complete, times out, or an error occurs
        emitter.onCompletion(() -> sseSessionManager.remove(userId));
        emitter.onTimeout(() -> sseSessionManager.remove(userId));
        emitter.onError(_ -> sseSessionManager.remove(userId));

        executorService.submit(() -> {
            sseSessionManager.add(userId, emitter);

            // A handshake message is sent immediately upon establishing a connection to prevent some gateways/browsers from directly terminating the connection due to a prolonged lack of data
            try {
                emitter.send(SseEmitter.event().name("connect").data("SSE connected"));
                // Publish connect success event
                myEventPublish.publishEvent(new SseConnectSuccessEvent(Long.valueOf(userId)));
            } catch (IOException e) {
                sseSessionManager.remove(userId);
                logger.error("Failed to send handshake message to user: {}", userId, e);
            }
        });

        return emitter;
    }

    @Override
    public void sendNotification(NotificationDto notificationDto) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        if (Objects.nonNull(currentUser) && Objects.isNull(notificationDto.getSenderId())) {
            notificationDto.setSenderId(currentUser.getId());
        }
        redissonUtils.addToStream(RedisKeyConstant.MESSAGE_STREAM_KEY, RedisKeyConstant.MESSAGE_STREAM_PAYLOAD_KEY, notificationDto);
    }

    @Override
    public void disconnect(String userId) {
        redissonUtils.publish(RedisKeyConstant.SSE_DISCONNECT_CHANNEL, userId);
    }
}
