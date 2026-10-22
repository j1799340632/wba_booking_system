package edu.unsw.api.controller.general;

import edu.unsw.core.annotation.RepeatRequestLimit;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.service.VerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/7/2026 16:13
 * @description VerifyController
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/verify")
@Tag(name = "VerifyController")
public class VerifyController {
    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @GetMapping("/getEmailVerificationCode")
    @Operation(summary = "Get email verification code")
    @RepeatRequestLimit(time = 60)
    public ResultEntity<String> getEmailVerificationCode() {
        verifyService.getEmailVerificationCode();
        return ResultEntity.success("Get email verification code successful");
    }
}
