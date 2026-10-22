package edu.unsw.api.controller.general;

import edu.unsw.core.annotation.RepeatRequestLimit;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.UserLoginDto;
import edu.unsw.domain.dto.UserResetOwnPasswordDto;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.UserVo;
import edu.unsw.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 19:50
 * @description User related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/user")
@Validated
@Tag(name = "User related interfaces")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login")
    @RepeatRequestLimit
    public ResultEntity<UserVo> login(@Validated @RequestBody UserLoginDto userLoginDto) {
        return ResultEntity.success("Login successfully", userService.login(userLoginDto));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout")
    public ResultEntity<CurrentUser> loginOut() {
        return ResultEntity.success("Logout successfully", userService.loginOut());
    }

    @PostMapping("/updateProfile")
    @Operation(summary = "Update user information")
    @RepeatRequestLimit
    public ResultEntity<UserVo> updateProfile(@Validated @RequestBody User user) {
        return ResultEntity.success("Update successfully", userService.updateProfile(user));
    }

    @GetMapping("/getProfile")
    @Operation(summary = "Get user information")
    public ResultEntity<UserVo> getProfile(Long id) {
        return ResultEntity.success("Get user profile successfully", userService.getProfile(id));
    }

    @PostMapping("/updateAvailableStatus")
    @Operation(summary = "Update if user available to receive booking")
    @RepeatRequestLimit
    public ResultEntity<Void> updateAvailableStatus(@NotNull(message = "Param isAvailable required")  Boolean isAvailable) {
        userService.updateAvailableStatus(isAvailable);
        return ResultEntity.success("Update successfully");
    }

    @PostMapping("/resetOwnPassword")
    @Operation(summary = "Reset the user own password")
    public ResultEntity<Void> resetOwnPassword(@Validated @RequestBody UserResetOwnPasswordDto userResetOwnPasswordDto) {
        userService.resetOwnPassword(userResetOwnPasswordDto);
        return ResultEntity.success("Reset successfully");
    }
}
