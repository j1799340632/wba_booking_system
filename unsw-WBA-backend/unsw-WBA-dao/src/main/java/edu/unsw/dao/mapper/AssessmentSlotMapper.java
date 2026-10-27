package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.AssessmentSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:26
 * @description AssessmentSlotMapper
 */
@Mapper
public interface AssessmentSlotMapper extends BaseMapper<AssessmentSlot> {
    @Select("SELECT * FROM assessment_slot WHERE id=#{id} FOR UPDATE")
    AssessmentSlot lockForBooking(@Param("id") Long id);

    @Select("SELECT * FROM assessment_slot WHERE id=#{id}")
    AssessmentSlot selectIncludingDeleted(@Param("id") Long id);

    @Select("""
        <script>SELECT * FROM assessment_slot WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<AssessmentSlot> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);

    /**
     * Query slot ids and creators without applying logical-delete filtering.
     * Completed assessments remain part of a supervisor's history even when
     * the associated slot has subsequently been deleted.
     *
     * @param supervisorIds supervisor user ids
     * @return assessment slots created by the supervisors
     */
    @Select("""
            <script>
            SELECT id, creator
            FROM assessment_slot
            WHERE creator IN
            <foreach collection="supervisorIds" item="supervisorId"
                     open="(" separator="," close=")">
                #{supervisorId}
            </foreach>
            </script>
            """)
    List<AssessmentSlot> selectByCreatorIdsIncludingDeleted(
            @Param("supervisorIds") Collection<Long> supervisorIds);
}
