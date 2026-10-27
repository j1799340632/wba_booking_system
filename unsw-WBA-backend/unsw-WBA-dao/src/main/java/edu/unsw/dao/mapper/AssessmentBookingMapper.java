package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.AssessmentBooking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:26
 * @description AssessmentBookingMapper
 */
@Mapper
public interface AssessmentBookingMapper extends BaseMapper<AssessmentBooking> {
    @Select("SELECT * FROM assessment_booking WHERE id=#{id} AND is_delete=0 FOR UPDATE")
    AssessmentBooking lockForManagement(@Param("id") Long id);

    @Select("SELECT * FROM assessment_booking WHERE creator=#{studentId} AND management_key=#{key}")
    AssessmentBooking findManagement(@Param("studentId") Long studentId, @Param("key") String key);
    @Select("SELECT * FROM assessment_booking WHERE creator=#{studentId} AND request_key=#{key}")
    AssessmentBooking findSubmission(@Param("studentId") Long studentId, @Param("key") String key);

    @Select("""
        SELECT COUNT(*) FROM assessment_booking b JOIN assessment_slot s ON s.id=b.assessment_slot_id
        WHERE b.is_delete=0 AND b.status IN (0,1)
          AND b.start_time < #{end} AND b.end_time > #{start}
          AND (b.creator=#{studentId} OR s.creator=#{supervisorId} OR s.id=#{slotId})
        """)
    long countBookingConflicts(@Param("studentId") Long studentId, @Param("supervisorId") Long supervisorId,
                              @Param("slotId") Long slotId, @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    @Select("""
        SELECT COUNT(*) FROM assessment_booking b JOIN assessment_slot s ON s.id=b.assessment_slot_id
        WHERE b.is_delete=0 AND b.status IN (0,1) AND b.id != #{excludedId}
          AND b.start_time < #{end} AND b.end_time > #{start}
          AND (b.creator=#{studentId} OR s.creator=#{supervisorId} OR s.id=#{slotId})
        """)
    long countReplacementConflicts(@Param("studentId") Long studentId, @Param("supervisorId") Long supervisorId,
                                  @Param("slotId") Long slotId, @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end, @Param("excludedId") Long excludedId);
}
