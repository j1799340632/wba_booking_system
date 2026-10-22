package edu.unsw.service;

import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.domain.entity.UserSettings;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 18:40
 * @description User settings service
 */
public interface UserSettingsService extends IService<UserSettings> {
    /**
     * Get user settings
     *
     * @return User settings
     */
    UserSettings getUserSettings();

    /**
     * Save user settings
     *
     * @param userSettings User settings
     * @return Whether save user settings successful
     */
    Boolean saveUserSettings(UserSettings userSettings);
}
