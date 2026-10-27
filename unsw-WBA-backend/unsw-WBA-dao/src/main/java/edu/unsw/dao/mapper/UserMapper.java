package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 20:58
 * @description User related database access
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM `user` WHERE id=#{id} AND is_delete=0 FOR UPDATE")
    User lockBookingParticipant(@Param("id") Long id);
}
