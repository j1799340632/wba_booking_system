package edu.unsw.service.scheculer;

import edu.unsw.service.sse.SseSessionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 14/7/2026 21:55
 * @description SseHeartbeatScheduler
 */
@Component
public class SseHeartbeatScheduler {
    private final SseSessionManager sseSessionManager;

    public SseHeartbeatScheduler(SseSessionManager sseSessionManager) {
        this.sseSessionManager = sseSessionManager;
    }

    @Scheduled(fixedDelay = 25000)
    public void sendSseHeartbeat() {
        sseSessionManager.broadcastHeartbeat();
    }
}
