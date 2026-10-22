package edu.unsw.service.event;

import org.springframework.context.ApplicationEvent;

import java.time.Clock;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/7/16 14:14
 * @description LoginSuccessEvent
 */
public class SseConnectSuccessEvent extends ApplicationEvent {
    public SseConnectSuccessEvent(Long userId) {
        super(userId);
    }

    public SseConnectSuccessEvent(Long userId, Clock clock) {
        super(userId, clock);
    }
}
