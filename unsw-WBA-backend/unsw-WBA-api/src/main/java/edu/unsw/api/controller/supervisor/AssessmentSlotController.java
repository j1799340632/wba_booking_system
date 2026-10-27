package edu.unsw.api.controller.supervisor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.AssessmentSlotSaveDto;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.UpcomingSessionVo;
import edu.unsw.domain.vo.SupervisorBookingResult;
import edu.unsw.service.AssessmentSlotService;
import edu.unsw.domain.dto.AssessmentCompletionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:28
 * @description AssessmentSlot related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.SUPERVISOR_PREFIX + "/assessmentSlot")
@Validated
@Tag(name = "AssessmentSlot related interfaces")
public class AssessmentSlotController {
    private final AssessmentSlotService assessmentSlotService;

    public AssessmentSlotController(AssessmentSlotService assessmentSlotService) {
        this.assessmentSlotService = assessmentSlotService;
    }

    @GetMapping("/getAssessmentSlotList")
    @Operation(summary = "getAssessmentSlotList")
    public ResultEntity<IPage<AssessmentSlotVo>> getAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return ResultEntity.success("Get assessment slot list successfully", assessmentSlotService.getAssessmentSlotList(assessmentSlotQueryDto));
    }

    @PostMapping("/saveAssessmentSlot")
    @Operation(summary = "saveAssessmentSlot")
    public ResultEntity<AssessmentSlotVo> saveAssessmentSlot(@Validated @RequestBody AssessmentSlotSaveDto assessmentSlotSaveDto) {
        return ResultEntity.success("Save assessment slot successfully", assessmentSlotService.saveAssessmentSlot(assessmentSlotSaveDto));
    }

    @PostMapping("/toggleAssessmentSlotsEnableDisable")
    @Operation(summary = "toggleAssessmentSlotsEnableDisable")
    public ResultEntity<Boolean> toggleAssessmentSlotsEnableDisable(@RequestParam List<Long> ids, @RequestParam Boolean isEnable) {
        return ResultEntity.success("Toggle assessment slots enable/disable successfully", assessmentSlotService.toggleAssessmentSlotsEnableDisable(ids, isEnable));
    }

    @PostMapping("/delAssessmentSlot")
    @Operation(summary = "delAssessmentSlot")
    public ResultEntity<Boolean> delAssessmentSlot(@NotNull(message = "Slot id required") Long id) {
        return ResultEntity.success("Delete assessment slot successfully", assessmentSlotService.delAssessmentSlot(id));
    }

    @GetMapping("/getUpcomingSession")
    @Operation(summary = "getUpcomingSession")
    public ResultEntity<IPage<UpcomingSessionVo>> getUpcomingSession(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return ResultEntity.success("Get upcoming session successfully", assessmentSlotService.getUpcomingSession(assessmentSlotQueryDto));
    }

    @GetMapping("/getApprovedSession")
    @Operation(summary = "getApprovedSession")
    public ResultEntity<IPage<UpcomingSessionVo>> getApprovedSession(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return ResultEntity.success("Get approved session successfully", assessmentSlotService.getApprovedSession(assessmentSlotQueryDto));
    }

    @PostMapping("/approveSlot")
    @Operation(summary = "approveSlot")
    public ResultEntity<SupervisorBookingResult> approveSlot(@NotNull(message = "Slot id required") Long id, @NotNull(message = "Booking id required") Long bookingId, @RequestParam @Min(0) Integer version) {
        return ResultEntity.success("Approve slot successfully",assessmentSlotService.approveSlot(id, bookingId, version));
    }

    @PostMapping("/declineSlot")
    @Operation(summary = "declineSlot")
    public ResultEntity<SupervisorBookingResult> declineSlot(@NotNull(message = "Slot id required") Long id, @NotNull(message = "Booking id required") Long bookingId, @NotNull(message = "Expected slot status") Boolean isEnable, @RequestParam @Min(0) Integer version) {
        return ResultEntity.success("Decline slot successfully",assessmentSlotService.declineSlot(id, bookingId, isEnable, version));
    }

    @PostMapping("/cancelSlot")
    @Operation(summary = "cancelSlot")
    public ResultEntity<SupervisorBookingResult> cancelSlot(@NotNull(message = "Slot id required") Long id, @NotNull(message = "Booking id required") Long bookingId, @NotNull(message = "Expected slot status") Boolean isEnable, @RequestParam @Min(0) Integer version) {
        return ResultEntity.success("Cancel slot successfully",assessmentSlotService.cancelSlot(id, bookingId, isEnable, version));
    }

    @PostMapping("/markAssessmentCompleted")
    @Operation(summary = "Mark assessment completed")
    public ResultEntity<SupervisorBookingResult> markAssessmentCompleted(
            @Validated @RequestBody AssessmentCompletionDto assessmentCompletionDto) {
        return ResultEntity.success("Mark assessment completed successfully",assessmentSlotService.markAssessmentCompleted(assessmentCompletionDto));
    }

    @PostMapping("/updateAssessmentResult")
    @Operation(summary = "Correct a completed assessment result without changing its completion date")
    public ResultEntity<SupervisorBookingResult> updateAssessmentResult(@Validated @RequestBody AssessmentCompletionDto request) {
        return ResultEntity.success("Assessment result updated",assessmentSlotService.updateAssessmentResult(request));
    }
}
