package edu.unsw.api.controller.student;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.domain.vo.AssessmentHistoryVo;
import edu.unsw.service.StudentBookingService;
import edu.unsw.domain.dto.BaseQueryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author WBA Connect
 * @date 24/6/2026 21:27
 * @description Student booking-related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.STUDENT_PREFIX + "/assessmentBooking")
@Validated
@Tag(name = "Student booking related interfaces")
public class StudentBookingController {
    private final StudentBookingService studentBookingService;

    public StudentBookingController(StudentBookingService studentBookingService) {
        this.studentBookingService = studentBookingService;
    }

    @GetMapping("/getAvailableAssessmentSlotList")
    @Operation(summary = "getAvailableAssessmentSlotList")
    public ResultEntity<IPage<AssessmentSlotVo>> getAvailableAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return ResultEntity.success("Get assessment slot list successfully", studentBookingService.getAvailableAssessmentSlotList(assessmentSlotQueryDto));
    }

    @PostMapping("/bookings")
    @Operation(summary = "Create booking")
    public ResultEntity<BookingVo> createBooking(@Validated @RequestBody AssessmentBookingSaveDto assessmentBookingSaveDto) {
        return ResultEntity.success("Create booking successful", studentBookingService.createBooking(assessmentBookingSaveDto));
    }

    @GetMapping("/bookings/my")
    @Operation(summary = "Get my bookings by page")
    public ResultEntity<IPage<BookingVo>> getMyBookings(BaseQueryDto baseQueryDto) {
        return ResultEntity.success(
                "Query my bookings successful",
                studentBookingService.getMyBookings(baseQueryDto)
        );
    }

    @GetMapping("/bookings/history")
    @Operation(summary = "Get completed assessment history by page")
    public ResultEntity<IPage<AssessmentHistoryVo>> getAssessmentHistory(BaseQueryDto baseQueryDto) {
        return ResultEntity.success(
                "Query assessment history successful",
                studentBookingService.getAssessmentHistory(baseQueryDto)
        );
    }

    @PostMapping("/cancelBooking")
    @Operation(summary = "Cancel booking")
    public ResultEntity<Boolean> cancelBooking(@RequestParam Long id) {
        return ResultEntity.success("Cancel booking successful", studentBookingService.cancelBooking(id));
    }

    @GetMapping("/exportAssessmentHistoryExcel")
    @Operation(summary = "Export assessment history excel")
    public void exportAssessmentHistoryExcel(AssessmentSlotQueryDto assessmentSlotQueryDto, HttpServletResponse response) {
        studentBookingService.exportAssessmentHistoryExcel(assessmentSlotQueryDto, response);
    }

}
