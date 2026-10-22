package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.dao.mapper.UserSettingsMapper;
import edu.unsw.domain.entity.UserSettings;
import edu.unsw.service.UserSettingsService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 18:40
 * @description User settings service implementation
 */
@Service
public class UserSettingsServiceImpl extends ServiceImpl<UserSettingsMapper, UserSettings> implements UserSettingsService {
    @Override
    public UserSettings getUserSettings() {
        CurrentUser currentUser = CurrentUserUtils.getUser();

        UserSettings one = this.getOne(
                Wrappers.<UserSettings>lambdaQuery().eq(UserSettings::getUserId, currentUser.getId())
        );

        // Init default settings if not found
        if (Objects.isNull(one)) {
            one = new UserSettings();
            one.setUserId(currentUser.getId());
            one.setIsReceiveEmail(true);
            one.setIsMessageSound(true);
            this.save(one);
        }

        return one;
    }

    @Override
    public Boolean saveUserSettings(UserSettings userSettings) {
        return this.update(
                userSettings,
                Wrappers.<UserSettings>lambdaUpdate().eq(UserSettings::getUserId, userSettings.getUserId())
        );
    }
}
