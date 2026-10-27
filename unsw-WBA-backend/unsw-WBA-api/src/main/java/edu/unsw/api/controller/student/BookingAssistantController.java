package edu.unsw.api.controller.student;

import edu.unsw.core.annotation.RepeatRequestLimit;
import edu.unsw.core.annotation.AssistantObserved;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.UserRoleKeyConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.dto.BookingAssistantRequest;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.BookingConfirmationRequest;
import edu.unsw.domain.dto.BookingManagementRequest;
import edu.unsw.domain.dto.BookingReplacementQueryDto;
import edu.unsw.domain.vo.BookingManagementVo;
import edu.unsw.service.BookingManagementService;
import edu.unsw.domain.vo.BookingDraftVo;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.service.assistant.BookingConfirmationService;
import edu.unsw.domain.vo.BookingAssistantResult;
import edu.unsw.service.assistant.BookingAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student/bookingAssistant")
public class BookingAssistantController {
    private final BookingAssistantService assistant;
    private final BookingConfirmationService confirmation;
    private final BookingManagementService management;
    public BookingAssistantController(BookingAssistantService assistant, BookingConfirmationService confirmation, BookingManagementService management) {
        this.assistant = assistant; this.confirmation = confirmation; this.management=management;
    }

    @PostMapping("/management/prepare")
    @AssistantObserved("PREPARE_MANAGEMENT")
    @RepeatRequestLimit(time=60,count=20)
    public ResultEntity<BookingManagementVo> prepareManagement(@Valid @RequestBody BookingManagementRequest request) {
        requireStudent(); return ResultEntity.success("Review booking changes",management.prepare(request));
    }

    @PostMapping("/management/confirm")
    @AssistantObserved("CONFIRM_MANAGEMENT")
    @RepeatRequestLimit(time=60,count=20)
    public ResultEntity<BookingManagementVo> confirmManagement(@Valid @RequestBody BookingConfirmationRequest request) {
        requireStudent(); return ResultEntity.success("Booking changes saved",management.confirm(request));
    }

    @PostMapping("/management/replacements")
    @AssistantObserved("REPLACEMENTS")
    @RepeatRequestLimit(time=60,count=20)
    public ResultEntity<BookingAssistantResult> replacements(@Valid @RequestBody BookingReplacementQueryDto request) {
        requireStudent(); return ResultEntity.success("Replacement search completed",assistant.replacementOptions(request));
    }

    @PostMapping("/prepare")
    @AssistantObserved("PREPARE_BOOKING")
    @RepeatRequestLimit(time = 60, count = 20)
    public ResultEntity<BookingDraftVo> prepare(@Valid @RequestBody AssessmentBookingSaveDto request) {
        requireStudent();
        return ResultEntity.success("Review the current session details", confirmation.prepare(request));
    }

    @PostMapping("/confirm")
    @AssistantObserved("CONFIRM_BOOKING")
    @RepeatRequestLimit(time = 60, count = 20)
    public ResultEntity<BookingVo> confirm(@Valid @RequestBody BookingConfirmationRequest request) {
        requireStudent();
        return ResultEntity.success("Booking request saved; awaiting supervisor approval", confirmation.confirm(request));
    }

    @PostMapping("/search")
    @AssistantObserved("SEARCH")
    @RepeatRequestLimit(time = 60, count = 10)
    public ResultEntity<BookingAssistantResult> search(@Valid @RequestBody BookingAssistantRequest request) {
        requireStudent();
        return ResultEntity.success("Assistant search completed", assistant.search(request));
    }

    private void requireStudent() {
        var user = CurrentUserUtils.getUser();
        if (user == null || !UserRoleKeyConstant.STUDENT.equals(user.getRoleKey())) {
            throw new BusinessException(ResponseEnum.NO_PERMISSION);
        }
    }
}
