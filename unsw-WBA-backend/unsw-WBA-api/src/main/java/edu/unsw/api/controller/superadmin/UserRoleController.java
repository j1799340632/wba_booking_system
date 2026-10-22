package edu.unsw.api.controller.superadmin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.UserRoleQueryDto;
import edu.unsw.core.entity.UserRole;
import edu.unsw.domain.vo.UserRoleVo;
import edu.unsw.service.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 19:50
 * @description User role interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.SUPER_ADMIN_PREFIX + "/userRole")
@Tag(name = "User role interfaces")
public class UserRoleController {
    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/getRoleList")
    @Operation(summary = "Get user role list")
    public ResultEntity<IPage<UserRoleVo>> getRoleList(UserRoleQueryDto userRoleQueryDto) {
        return ResultEntity.success("Get user role list successfully", userRoleService.getRoleList(userRoleQueryDto));
    }

    @GetMapping("/getEnabledRoleList")
    @Operation(summary = "Get enabled user role list")
    public ResultEntity<List<UserRole>> getEnabledRoleList() {
        return ResultEntity.success("Get enabled user role list successfully", userRoleService.getEnabledRoleList());
    }

    @PostMapping("/addRole")
    @Operation(summary = "Add a role")
    public ResultEntity<Boolean> addRole(@Validated @RequestBody UserRole userRole) {
        return ResultEntity.success("Add user role successfully", userRoleService.addRole(userRole));
    }

    @PostMapping("/editRole")
    @Operation(summary = "Edit a role")
    public ResultEntity<Boolean> editRole(@Validated @RequestBody UserRole userRole) {
        return ResultEntity.success("Edit a role successfully", userRoleService.editRole(userRole));
    }

    @PostMapping("/delRole")
    @Operation(summary = "Delete a role")
    public ResultEntity<Boolean> delRole(@NotNull(message = "User role ID cannot be null") Long id) {
        return ResultEntity.success("Delete a role successfully", userRoleService.delRole(id));
    }

    @PostMapping("/changeRoleStatus")
    @Operation(summary = "Change a role status")
    public ResultEntity<Boolean> changeRoleStatus(@NotNull(message = "Id cannot be null") Long id, @NotNull(message = "Status cannot be null") Boolean targetStatus) {
        return ResultEntity.success("Change a role status successfully", userRoleService.changeRoleStatus(id, targetStatus));
    }
}
