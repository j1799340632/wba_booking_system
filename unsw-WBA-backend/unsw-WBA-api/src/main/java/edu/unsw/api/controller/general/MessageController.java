package edu.unsw.api.controller.general;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.vo.MessageVo;
import edu.unsw.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:47
 * @description MessageController
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/message")
@Tag(name = "Message interfaces")
@Validated
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/getMessageList")
    @Operation(summary = "Get message list")
    public ResultEntity<List<MessageVo>> getMessageList() {
        return ResultEntity.success("Get message list successfully", messageService.getMessageList());
    }

    @PostMapping("/readMessages")
    @Operation(summary = "Read messages")
    public ResultEntity<Void> readMessages(@NotNull(message = "Message ids cannot be null") Long[] ids) {
        messageService.readMessages(ids);
        return ResultEntity.success("Read messages successfully");
    }
}
