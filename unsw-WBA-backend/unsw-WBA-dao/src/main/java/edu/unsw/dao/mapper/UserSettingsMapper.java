package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.UserSettings;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 00:26
 * @description UserSettingsMapper
 */
@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {
}
