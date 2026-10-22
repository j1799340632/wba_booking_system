package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:43
 * @description Message-related database access
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
