package edu.unsw.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.AssessmentSlotSaveDto;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.UpcomingSessionVo;
import edu.unsw.domain.vo.SupervisorBookingResult;
import edu.unsw.domain.dto.AssessmentCompletionDto;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:42
 * @description AssessmentSlot related services
 */
public interface AssessmentSlotService extends IService<AssessmentSlot> {
    /**
     * Get an assessment slot list
     * @param assessmentSlotQueryDto assessment slot query parameters
     * @return assessment slot list
     */
    IPage<AssessmentSlotVo> getAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto);

    /**
     * Save an assessment slot
     * @param assessmentSlotSaveDto assessment slot save dto
     * @return assessment slot
     */
    AssessmentSlotVo saveAssessmentSlot(AssessmentSlotSaveDto assessmentSlotSaveDto);

    /**
     * Change Toggle assessment slots enable/disable
     * @param ids assessment slot id list
     * @param isEnable expected slot status
     * @return is changed
     */
    Boolean toggleAssessmentSlotsEnableDisable(List<Long> ids, Boolean isEnable);

    /**
     * Delete an assessment slot
     * @param id assessment slot id
     * @return is deleted
     */
    Boolean delAssessmentSlot(Long id);

    /**
     * Get the upcoming session list
     * @param assessmentSlotQueryDto assessment slot query parameters
     * @return upcoming session list
     */
    IPage<UpcomingSessionVo> getUpcomingSession(AssessmentSlotQueryDto assessmentSlotQueryDto);

    /**
     * Get the approved session list
     * @param assessmentSlotQueryDto assessment slot query parameters
     * @return approved session list
     */
    IPage<UpcomingSessionVo> getApprovedSession(AssessmentSlotQueryDto assessmentSlotQueryDto);

    /**
     * Approve an assessment slot
     * @param id assessment slot id
     * @param bookingId booking id
     */
    SupervisorBookingResult approveSlot(Long id, Long bookingId, Integer version);

    /**
     * Decline an assessment slot
     * @param id assessment slot id
     * @param bookingId booking id
     * @param isEnable expected slot status
     */
    SupervisorBookingResult declineSlot(Long id, Long bookingId, Boolean isEnable, Integer version);

    /**
     * Cancel an assessment slot
     * @param id assessment slot id
     * @param bookingId booking id
     * @param isEnable expected slot status
     */
    SupervisorBookingResult cancelSlot(Long id, Long bookingId, Boolean isEnable, Integer version);

    /**
     * Mark an approved assessment as completed
     *
     * @param assessmentCompletionDto completion data
     */
    SupervisorBookingResult markAssessmentCompleted(AssessmentCompletionDto assessmentCompletionDto);

    SupervisorBookingResult updateAssessmentResult(AssessmentCompletionDto assessmentCompletionDto);
}
