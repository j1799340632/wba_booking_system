package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.GoogleCalendarConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Stan
 * @date 26/7/2026
 * @description Google Calendar connection mapper
 */
@Mapper
public interface GoogleCalendarConnectionMapper
        extends BaseMapper<GoogleCalendarConnection> {
}