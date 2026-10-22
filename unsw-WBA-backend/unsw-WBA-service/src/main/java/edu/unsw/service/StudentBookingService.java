package edu.unsw.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.domain.vo.AssessmentHistoryVo;
import edu.unsw.domain.dto.BaseQueryDto;
import jakarta.servlet.http.HttpServletResponse;


/**
 * @author WBA Connect
 * @date 24/6/2026 21:24
 * @description Student booking-related services
 */
public interface StudentBookingService extends IService<AssessmentBooking> {
    /**
     * Get the available assessment slot list
     * @param assessmentSlotQueryDto query parameters
     * @return the available assessment slot page
     */
    IPage<AssessmentSlotVo> getAvailableAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto);

    /**
     * Create a booking for the current logged-in student
     *
     * @param assessmentBookingSaveDto Create a booking request
     * @return Booking view object
     */
    BookingVo createBooking(AssessmentBookingSaveDto assessmentBookingSaveDto);

    /**
     * Query bookings of the current logged-in student by page
     *
     * @param baseQueryDto pagination parameters
     * @return paginated bookings
     */
    IPage<BookingVo> getMyBookings(BaseQueryDto baseQueryDto);

    /**
     * Query completed assessment history of the current logged in student by page
     *
     * @param baseQueryDto pagination parameters
     * @return paginated completed assessment history
     */
    IPage<AssessmentHistoryVo> getAssessmentHistory(
            BaseQueryDto baseQueryDto);

    /**
     * Cancel booking by booking id
     *
     * @param id Booking id
     * @return Whether cancellation succeeds
     */
    Boolean cancelBooking(Long id);

    /**
     * Export assessment history excel
     *
     * @param assessmentSlotQueryDto query parameters
     * @param response HttpServletResponse
     */
    void exportAssessmentHistoryExcel(AssessmentSlotQueryDto assessmentSlotQueryDto, HttpServletResponse response);
}
