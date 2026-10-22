package edu.unsw.api.controller.general;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.entity.UserSettings;
import edu.unsw.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 18:39
 * @description User settings interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/userSettings")
@Validated
@Tag(name = "User settings interfaces")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping("/getUserSettings")
    @Operation(summary = "Get user settings")
    public ResultEntity<UserSettings> getUserSettings() {
        return ResultEntity.success("Get user settings successful", userSettingsService.getUserSettings());
    }

    @PostMapping("/saveUserSettings")
    @Operation(summary = "Save user settings")
    public ResultEntity<Boolean> saveUserSettings(@RequestBody UserSettings userSettings) {
        return ResultEntity.success("Save user settings successful", userSettingsService.saveUserSettings(userSettings));
    }
}
