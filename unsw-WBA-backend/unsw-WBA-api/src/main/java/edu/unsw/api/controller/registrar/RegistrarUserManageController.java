package edu.unsw.api.controller.registrar;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.UserQueryDto;
import edu.unsw.domain.dto.UserResetPasswordDto;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.UserVo;
import edu.unsw.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 20/7/2026 21:08
 * @description Registrars operate users' interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.REGISTRAR_PREFIX + "/userManage")
@Tag(name = "Registrar operate users interfaces")
@Validated
public class RegistrarUserManageController {
    private final UserService userService;

    public RegistrarUserManageController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/getUserList")
    @Operation(summary = "Get all user list")
    public ResultEntity<IPage<UserVo>> getUserList(UserQueryDto userQueryDto) {
        return ResultEntity.success("Get user list successful", userService.getUserList(userQueryDto));
    }

    @PostMapping("/addUser")
    @Operation(summary = "Add a new user")
    public ResultEntity<User> addUser(@Validated @RequestBody User user) {
        return ResultEntity.success("Add user successfully", userService.addUser(user));
    }

    @PostMapping("/importBatchUser")
    @Operation(summary = "Upload a file and parse to add batch users")
    public ResultEntity<Integer> importBatchUser(@RequestPart("file") MultipartFile multipartFile) {
        return ResultEntity.success("Import batch user successfully", userService.importBatchUser(multipartFile));
    }

    @GetMapping("/downloadUserImportTemplate")
    @Operation(summary = "Download a user import template")
    public void downloadUserTemplate(HttpServletResponse response) {
        userService.downloadUserImportTemplate(response);
    }

    @PostMapping("/resetPassword")
    @Operation(summary = "Reset a certain user's password")
    public ResultEntity<Void> resetPassword(@Validated @RequestBody UserResetPasswordDto userResetPasswordDto) {
        userService.resetPassword(userResetPasswordDto);
        return ResultEntity.success("Reset successfully");
    }

    @PostMapping("/updateOtherProfile")
    @Operation(summary = "Update other users information")
    public ResultEntity<UserVo> updateOtherProfile(@Validated @RequestBody User user) {
        return ResultEntity.success("Update the other user successfully", userService.updateOtherProfile(user));
    }

    @PostMapping("/changeUserStatus")
    @Operation(summary = "Change the user status")
    public ResultEntity<Boolean> changeUserStatus(@NotNull(message = "User id cannot be null") Long userId, @NotNull(message = "Status cannot be null") Boolean targetStatus) {
        return ResultEntity.success("Change the user status successfully", userService.changeUserStatus(userId, targetStatus));
    }

    @PostMapping("/delUser")
    @Operation(summary = "Del user password")
    public ResultEntity<Boolean> delUser(@NotNull(message = "User id cannot be null") Long targetUserId) {
        return ResultEntity.success("Del user successfully", userService.delUser(targetUserId));
    }
}
