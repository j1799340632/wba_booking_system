package edu.unsw.api.controller.general;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 13/7/2026 14:45
 * @description SSE Controller
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/sse")
@Tag(name = "SSE interfaces")
public class SseController {
    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/getTicket")
    @Operation(summary = "Get ticket to connect to SSE")
    public ResultEntity<String> getTicket() {
        return ResultEntity.success("Get SSE ticket successfully", sseService.getTicket());
    }

    /**
     * Front-end interface for establishing SSE persistent connections
     * Note: Must specify produces = MediaType.TEXT_EVENT_STREAM_VALUE
     */
    @GetMapping(value = "/subscribe/{userId}/{ticket}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Establishing SSE connection")
    public SseEmitter subscribe(@PathVariable String userId, @PathVariable String ticket, HttpServletResponse response) {
        return sseService.subscribe(userId, ticket, response);
    }

    @GetMapping("/disconnect/{userId}")
    @Operation(summary = "Disconnect to SSE")
    public ResultEntity<String> disconnect(@PathVariable String userId) {
        sseService.disconnect(userId);
        return ResultEntity.success("Disconnect successful");
    }

    /**
     * Notify the corresponding user
     */
    @PostMapping("/sendNotification")
    @Operation(summary = "Send notification to supervisor")
    public ResultEntity<String> sendNotification(@RequestBody NotificationDto notificationDto) {
        sseService.sendNotification(notificationDto);
        return ResultEntity.success("Business processing successful, distributed broadcast has been triggered");
    }
}
