package edu.unsw.service;

import edu.unsw.domain.dto.NotificationDto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 14/7/2026 21:59
 * @description SSE service interface
 */
public interface SseService {
    /**
     * Generate a ticket for SSE connection
     *
     * @return ticket
     */
    String getTicket();

    /**
     * Establishing SSE connection
     *
     * @param userId user id
     * @param ticket ticket
     * @param response response
     * @return SseEmitter
     */
    SseEmitter subscribe(String userId, String ticket, HttpServletResponse response);

    /**
     * Notify the corresponding user
     *
     * @param notificationDto notification dto
     */
    void sendNotification(NotificationDto notificationDto);

    /**
     * Disconnect to SSE
     *
     * @param userId user id
     */
    void disconnect(String userId);
}
