package edu.unsw.service.sse;

import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.utils.RedissonUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 13/7/2026 15:28
 * @description SseSessionManager
 */
@Component
public class SseSessionManager {
    private final Logger logger = LoggerFactory.getLogger(SseSessionManager.class);

    private final Map<String, SseEmitter> localEmitters = new ConcurrentHashMap<>();

    private Integer listenerId = null;

    private final RedissonUtils redissonUtils;

    public SseSessionManager(RedissonUtils redissonUtils) {
        this.redissonUtils = redissonUtils;
    }

    @PostConstruct
    public void initGlobalSubscriber() {
        listenerId = redissonUtils.subscribe(
                RedisKeyConstant.SSE_DISCONNECT_CHANNEL,
                String.class,
                this::remove
        );

        logger.info("Successfully registered integrated SSE global logout subscriber.");
    }

    public void add(String userId, SseEmitter emitter) {
        localEmitters.put(userId, emitter);
    }

    public void remove(String userId) {
        SseEmitter emitter = localEmitters.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
                logger.info("Remove SSE connection for user [{}].", userId);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }
    }

    public SseEmitter get(String userId) {
        return localEmitters.get(userId);
    }

    public void broadcastHeartbeat() {
        localEmitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                // Clear disconnected channels to prevent memory leaks
                this.remove(userId);
                emitter.completeWithError(e);
            }
        });
    }

    @PreDestroy
    public void close() {
        logger.info("Shutting down SseEmitterRegistry. Cleaning up local listeners...");
        try {
           redissonUtils.unsubscribe(RedisKeyConstant.SSE_DISCONNECT_CHANNEL, listenerId);
           logger.info("Successfully removed Redisson SSE global logout listener [{}].", listenerId);
        } catch (Exception e) {
            logger.warn("Failed to remove Redisson SSE global logout listener gracefully.", e);
        }

        localEmitters.clear();
    }
}
