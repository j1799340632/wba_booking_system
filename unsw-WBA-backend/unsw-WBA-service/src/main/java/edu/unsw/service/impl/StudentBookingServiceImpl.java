package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.constant.UserRoleKeyConstant;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.BaseQueryDto;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.domain.vo.AssessmentHistoryVo;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import edu.unsw.service.StudentBookingService;
import edu.unsw.service.BookingSubmissionService;
import edu.unsw.service.BookingManagementService;
import edu.unsw.service.StudentSlotSearchService;
import edu.unsw.service.utils.FastExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author WBA Connect
 * @date 24/6/2026 21:25
 * @description Student booking-related services implementation
 */
@Service
public class StudentBookingServiceImpl extends ServiceImpl<AssessmentBookingMapper, AssessmentBooking> implements StudentBookingService {
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final UserMapper userMapper;
    private final BookingManagementService bookingManagementService;
    private final BookingSubmissionService bookingSubmissionService;
    private final StudentSlotSearchService studentSlotSearchService;

    public StudentBookingServiceImpl(AssessmentSlotMapper assessmentSlotMapper, UserMapper userMapper, BookingManagementService bookingManagementService, BookingSubmissionService bookingSubmissionService, StudentSlotSearchService studentSlotSearchService) {
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.userMapper = userMapper;
        this.bookingManagementService = bookingManagementService;
        this.bookingSubmissionService = bookingSubmissionService;
        this.studentSlotSearchService = studentSlotSearchService;
    }

    @Override
    public Page<AssessmentSlotVo> getAvailableAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return studentSlotSearchService.search(assessmentSlotQueryDto);
    }

    @Override
    public BookingVo createBooking(AssessmentBookingSaveDto request) {
        return bookingSubmissionService.submit(request, null);
    }

    @Override
    public Page<BookingVo> getMyBookings(BaseQueryDto baseQueryDto) {
        CurrentUser currentUser = CurrentUserUtils.getUser();

        Page<AssessmentBooking> bookingPage = this.lambdaQuery()
                .eq(AssessmentBooking::getCreator, currentUser.getId())
                .orderByDesc(AssessmentBooking::getId)
                .page(new Page<>(
                        baseQueryDto.getPageNum(),
                        baseQueryDto.getPageQuantity()
                ));

        Page<BookingVo> bookingVoPage = new Page<>();
        BeanUtils.copyProperties(bookingPage, bookingVoPage);

        List<AssessmentBooking> bookings = bookingPage.getRecords();
        if (bookings.isEmpty()) {
            return bookingVoPage;
        }

        Set<Long> slotIds = bookings.stream()
                .map(AssessmentBooking::getAssessmentSlotId)
                .collect(Collectors.toSet());

        // Keep actual owned booking records accessible when their publication template is archived.
        List<AssessmentSlot> slotList = assessmentSlotMapper.selectByIdsIncludingDeleted(slotIds);

        Map<Long, AssessmentSlot> slotMap = slotList.stream()
                .collect(Collectors.toMap(
                        AssessmentSlot::getId,
                        slot -> slot,
                        (a, b) -> a
                ));

        List<BookingVo> bookingVoList = new ArrayList<>(bookings.size());
        for (AssessmentBooking booking : bookings) {
            AssessmentSlot slot = slotMap.get(booking.getAssessmentSlotId());
            if (Objects.nonNull(slot)) {
                bookingVoList.add(buildBookingVo(booking, slot));
            }
        }

        bookingVoPage.setRecords(bookingVoList);
        return bookingVoPage;
    }

    @Override
    public Page<AssessmentHistoryVo> getAssessmentHistory(BaseQueryDto baseQueryDto) {
        Integer pageNum = baseQueryDto.getPageNum();
        Integer pageQuantity = baseQueryDto.getPageQuantity();

        LambdaQueryChainWrapper<AssessmentBooking> queryChainWrapper = this.lambdaQuery()
                .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.COMPLETED.getCode())
                .between(
                        Objects.nonNull(baseQueryDto.getStartTime()) && Objects.nonNull(baseQueryDto.getEndTime()),
                        AssessmentBooking::getCompletedTime,
                        baseQueryDto.getStartTime(),
                        baseQueryDto.getEndTime()
                )
                .orderByDesc(AssessmentBooking::getCompletedTime);

        CurrentUser currentUser = CurrentUserUtils.getUser();
        Long userId = currentUser.getId();
        // If user is student, can only query his own assessment history
        if (Objects.equals(UserRoleKeyConstant.STUDENT, currentUser.getRoleKey())) {
            queryChainWrapper.eq(AssessmentBooking::getCreator, userId);
        } else if (Objects.equals(UserRoleKeyConstant.PLACEMENT_SUPERVISOR, currentUser.getRoleKey())) {
            List<AssessmentSlot> assessmentSlotList = assessmentSlotMapper.selectByCreatorIdsIncludingDeleted(List.of(userId));
            if (assessmentSlotList.isEmpty()) {
                return new Page<>(pageNum, pageQuantity);
            }

            queryChainWrapper.in(AssessmentBooking::getAssessmentSlotId, assessmentSlotList.stream().map(AssessmentSlot::getId).toList());
        }

        Page<AssessmentBooking> bookingPage = queryChainWrapper
                .page(new Page<>(
                        pageNum,
                        pageQuantity
                ));

        Page<AssessmentHistoryVo> historyPage = new Page<>();
        BeanUtils.copyProperties(bookingPage, historyPage);

        List<AssessmentBooking> bookings = bookingPage.getRecords();
        if (bookings.isEmpty()) {
            return historyPage;
        }

        Set<Long> slotIds = bookings.stream()
                .map(AssessmentBooking::getAssessmentSlotId)
                .collect(Collectors.toSet());

        Map<Long, AssessmentSlot> slotMap =
                assessmentSlotMapper.selectByIdsIncludingDeleted(slotIds)
                        .stream()
                        .collect(Collectors.toMap(
                                AssessmentSlot::getId,
                                slot -> slot,
                                (a, b) -> a
                        ));

        List<AssessmentHistoryVo> records = new ArrayList<>();

        for (AssessmentBooking booking : bookings) {
            AssessmentSlot slot = slotMap.get(booking.getAssessmentSlotId());

            if (Objects.isNull(slot)) {
                continue;
            }

            AssessmentHistoryVo historyVo = new AssessmentHistoryVo();

            BeanUtils.copyProperties(
                    buildBookingVo(booking, slot),
                    historyVo
            );

            historyVo.setScore(booking.getScore());
            historyVo.setFeedback(booking.getFeedback());
            historyVo.setCompletedTime(booking.getCompletedTime());
            Long creatorId = booking.getCreator();
            historyVo.setStudentId(creatorId);

            User student = userMapper.selectOne(
                    Wrappers.<User>lambdaQuery()
                            .eq(User::getId, creatorId)
                            .select(User::getName)
            );
            historyVo.setStudentName(student == null ? "Student unavailable" : student.getName());

            records.add(historyVo);
        }

        historyPage.setRecords(records);
        return historyPage;
    }

    @Override
    public Boolean cancelBooking(Long id) {
        return bookingManagementService.cancelDirect(id);
    }

    @Override
    public void exportAssessmentHistoryExcel(AssessmentSlotQueryDto assessmentSlotQueryDto, HttpServletResponse response) {
        String fileName = "Student_Scores_Report.xlsx";

        assessmentSlotQueryDto.setPageNum(1);
        assessmentSlotQueryDto.setPageQuantity(-1);
        List<AssessmentHistoryVo> assessmentHistoryVoList = getAssessmentHistory(assessmentSlotQueryDto).getRecords();
        List<String> columnTitleList = List.of(
                "Student ID",
                "Student Name",
                "Complete Time",
                "Final Score",
                "Feedback",
                "Supervisor ID",
                "Supervisor Name",
                "WBA Type",
                "Location",
                "Hospital",
                "Department"
        );
        List<List<Object>> columnValueList = new ArrayList<>();
        assessmentHistoryVoList.forEach(item -> {
            List<Object> singleColumnValueList = new ArrayList<>();
            singleColumnValueList.add(item.getStudentId());
            singleColumnValueList.add(item.getStudentName());
            singleColumnValueList.add(item.getCompletedTime());
            singleColumnValueList.add(item.getScore());
            singleColumnValueList.add(item.getFeedback());
            singleColumnValueList.add(item.getSupervisorId());
            singleColumnValueList.add(item.getSupervisorName());
            singleColumnValueList.add(item.getWbaType());
            singleColumnValueList.add(item.getLocation());
            singleColumnValueList.add(item.getHospital());
            singleColumnValueList.add(item.getDepartment());
            columnValueList.add(singleColumnValueList);
        });

        FastExcelUtils.exportExcel(columnTitleList, columnValueList, fileName, response);
    }

    private BookingVo buildBookingVo(AssessmentBooking booking, AssessmentSlot slot) {
        BookingVo vo = new BookingVo();
        vo.setBookingId(booking.getId());
        vo.setSlotId(booking.getAssessmentSlotId());

        User supervisor = userMapper.selectById(slot.getCreator());

        vo.setSupervisorId(slot.getCreator());
        vo.setSupervisorName(supervisor == null ? "Supervisor unavailable" : supervisor.getName());
        vo.setWbaType(slot.getType());
        vo.setHospital(slot.getHospital());
        vo.setDepartment(slot.getDepartment());
        vo.setLocation(slot.getLocation());
        vo.setWbaType(slot.getType());
        vo.setStartTime(booking.getStartTime());
        vo.setEndTime(booking.getEndTime());
        // Calculate the duration of the slot
        String duration = booking.getStartTime() != null && booking.getEndTime() != null && booking.getEndTime().isAfter(booking.getStartTime())
                ? Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes() + "min" : "Unknown";
        vo.setDurationMinutes(duration);
        vo.setStatus(booking.getStatus());
        vo.setVersion(booking.getVersion());
        vo.setManagementRestriction(bookingManagementService.restriction(booking));
        vo.setCanManage(vo.getManagementRestriction() == null);
        return vo;
    }
}
