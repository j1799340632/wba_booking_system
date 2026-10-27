package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.AssistantEvent;
import edu.unsw.domain.vo.AssistantInsightsVo.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantEventMapper extends BaseMapper<AssistantEvent> {
    @Select("""
        SELECT stage,outcome,action,COUNT(*) attempts,
            SUM(CASE WHEN alternative_groups>0 THEN 1 ELSE 0 END) with_alternatives,
            SUM(model_calls) model_calls,SUM(CASE WHEN usage_known=1 THEN model_calls ELSE 0 END) known_usage,
            SUM(CASE WHEN usage_known=1 THEN prompt_tokens ELSE 0 END) prompt_tokens,
            SUM(CASE WHEN usage_known=1 THEN completion_tokens ELSE 0 END) completion_tokens,
            SUM(CASE WHEN usage_known=1 THEN total_tokens ELSE 0 END) total_tokens
        FROM assistant_event WHERE created_at>=#{start} AND created_at<#{end}
        GROUP BY stage,outcome,action ORDER BY stage,outcome,action
        """)
    List<StageCount> counts(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

    @Select("""
        SELECT CAST(created_at AS DATE) date,
            SUM(CASE WHEN stage='SEARCH' THEN 1 ELSE 0 END) searches,
            SUM(CASE WHEN stage='SEARCH' AND outcome='READY' THEN 1 ELSE 0 END) ready,
            SUM(CASE WHEN stage='SEARCH' AND outcome='CLARIFY' THEN 1 ELSE 0 END) clarify,
            SUM(CASE WHEN stage='SEARCH' AND outcome='NO_RESULTS' THEN 1 ELSE 0 END) no_results,
            SUM(CASE WHEN stage='SEARCH' AND outcome='UNAVAILABLE' THEN 1 ELSE 0 END) unavailable,
            SUM(CASE WHEN stage='CONFIRM_BOOKING' AND outcome='SAVED' THEN 1 ELSE 0 END) new_booking_requests
        FROM assistant_event WHERE created_at>=#{start} AND created_at<#{end}
        GROUP BY CAST(created_at AS DATE) ORDER BY date
        """)
    List<Daily> days(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

    @Select("""
        SELECT model_name,SUM(model_calls) calls,
            SUM(CASE WHEN usage_known=1 THEN model_calls ELSE 0 END) known_usage,
            SUM(CASE WHEN usage_known=1 THEN total_tokens ELSE 0 END) total_tokens
        FROM assistant_event WHERE created_at>=#{start} AND created_at<#{end} AND model_calls>0
        GROUP BY model_name ORDER BY model_name
        """)
    List<ModelUsage> models(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

    @Select("SELECT elapsed_ms FROM assistant_event WHERE stage='SEARCH' AND created_at>=#{start} AND created_at<#{end} ORDER BY elapsed_ms,id LIMIT 1 OFFSET #{offset}")
    Long searchLatency(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("offset") long offset);

    @Select("SELECT MIN(created_at) FROM assistant_event")
    LocalDateTime firstRecordedAt();
}
