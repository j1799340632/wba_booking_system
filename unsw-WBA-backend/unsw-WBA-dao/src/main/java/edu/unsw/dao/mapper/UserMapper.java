package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 20:58
 * @description User related database access
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
