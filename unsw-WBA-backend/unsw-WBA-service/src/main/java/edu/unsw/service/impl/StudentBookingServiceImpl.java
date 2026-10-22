package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.constant.UserRoleKeyConstant;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.BaseQueryDto;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
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
import edu.unsw.service.enums.AssessmentSlotRepeatTypeEnum;
import edu.unsw.service.enums.AssessmentSlotStatusEnum;
import edu.unsw.service.utils.FastExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AssessmentSlotServiceImpl assessmentSlotServiceImpl;
    private final RedissonUtils redissonUtils;

    public StudentBookingServiceImpl(AssessmentSlotMapper assessmentSlotMapper, UserMapper userMapper, AssessmentSlotServiceImpl assessmentSlotServiceImpl, RedissonUtils redissonUtils) {
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.userMapper = userMapper;
        this.assessmentSlotServiceImpl = assessmentSlotServiceImpl;
        this.redissonUtils = redissonUtils;
    }

    @Override
    public Page<AssessmentSlotVo> getAvailableAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        return assessmentSlotServiceImpl.getAssessmentSlotVoPage(assessmentSlotQueryDto,
                queryWrapper ->
                        queryWrapper
                                .eq(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.AVAILABLE.getCode())
                                .eq(AssessmentSlot::getIsEnable, true)
                                .ge(AssessmentSlot::getEndTime, LocalDateTime.now(ZoneId.systemDefault()))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVo createBooking(AssessmentBookingSaveDto assessmentBookingSaveDto) {
        String lockKey = RedisKeyConstant.SLOT_LOCK_PREFIX + assessmentBookingSaveDto.getAssessmentSlotId();
        boolean isLocked = redissonUtils.tryLock(lockKey, 4, 3);
        if (!isLocked) {
            throw new BusinessException(ResponseEnum.LOCK_CONTROL);
        }

        try {
            AssessmentBooking assessmentBooking = new AssessmentBooking();
            BeanUtils.copyProperties(assessmentBookingSaveDto, assessmentBooking);
            // Check if the associated slot has been deleted
            AssessmentSlot slot = assessmentSlotMapper.selectById(assessmentBooking.getAssessmentSlotId());
            if (Objects.isNull(slot)) {
                throw new BusinessException(ResponseEnum.DATA_NOT_QUERIED.getCode(), "Availability slot does not exist");
            }

            if (!AssessmentSlotStatusEnum.AVAILABLE.getCode().equals(slot.getStatus())) {
                throw new BusinessException(ResponseEnum.FAILURE.getCode(), "Availability slot is not AVAILABLE");
            }

            // The slot has been changed, tell user data changed, refresh and retry
            if (!Objects.equals(slot.getVersion(), assessmentBookingSaveDto.getSlotVersion())) {
                throw new BusinessException(ResponseEnum.SLOT_CONFLICT.getCode(), "The slot data has been changed, please refresh and retry");
            }

            // Check if this period of the slot has been booked
            long count = this.count(
                    Wrappers.<AssessmentBooking>lambdaQuery()
                            .eq(AssessmentBooking::getAssessmentSlotId, assessmentBooking.getAssessmentSlotId())
                            .eq(AssessmentBooking::getStartTime, assessmentBooking.getStartTime())
                            .eq(AssessmentBooking::getEndTime, assessmentBooking.getEndTime())
                            .and(i -> i
                                    .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.PENDING.getCode())
                                    .or()
                                    .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.CONFIRMED.getCode())
                            )
            );
            if (count > 0) {
                throw new BusinessException(ResponseEnum.SLOT_CONFLICT);
            }

            // Save booking
            this.save(assessmentBooking);

            // Update the nextAvailableTime and status of the associated slot and no need to update version, so use updateWrapper to avoid auto-update
            LambdaUpdateChainWrapper<AssessmentSlot> updateChainWrapper = new LambdaUpdateChainWrapper<>(assessmentSlotMapper);

            // Repeat slot needs to update nextAvailableTime and not update status
            if (Objects.equals(AssessmentSlotRepeatTypeEnum.NO_REPEAT.getCode(), slot.getRepeatType())) {
                updateChainWrapper.set(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.PENDING.getCode());
            } else {
                LocalDateTime nextAvailableTime = assessmentSlotServiceImpl.calculateRemainAvailableTime(slot);
                if (Objects.isNull(nextAvailableTime)) {
                    // If no available time remaining, set status to pending
                    updateChainWrapper.set(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.PENDING.getCode());
                } else {
                    updateChainWrapper.set(AssessmentSlot::getNextAvailableTime, nextAvailableTime);
                }
            }

            updateChainWrapper
                    .eq(AssessmentSlot::getId, slot.getId())
                    .update();

            return buildBookingVo(assessmentBooking, slot);
        } finally {
            redissonUtils.unlock(lockKey);
        }
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

        List<AssessmentSlot> slotList =
                new LambdaQueryChainWrapper<>(assessmentSlotMapper)
                        .in(AssessmentSlot::getId, slotIds)
                        .list();

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
            List<AssessmentSlot> assessmentSlotList = assessmentSlotMapper.selectList(
                    Wrappers.<AssessmentSlot>lambdaQuery()
                            .eq(AssessmentSlot::getCreator, userId)
                            .select(AssessmentSlot::getId)
            );
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
                new LambdaQueryChainWrapper<>(assessmentSlotMapper)
                        .in(AssessmentSlot::getId, slotIds)
                        .list()
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
            historyVo.setStudentName(student.getName());

            records.add(historyVo);
        }

        historyPage.setRecords(records);
        return historyPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelBooking(Long id) {
        CurrentUser currentUser = CurrentUserUtils.getUser();

        AssessmentBooking booking = this.getById(id);
        if (Objects.isNull(booking)) {
            throw new BusinessException(ResponseEnum.DATA_NOT_QUERIED.getCode(), "Booking does not exist");
        }

        if (!Objects.equals(booking.getCreator(), currentUser.getId())) {
            throw new BusinessException(ResponseEnum.NO_PERMISSION);
        }

        // Students are not allowed to cancel bookings that have been confirmed by their supervisor within 48 hours of the booking start date
        long startInMinutes = Duration.between(
                ZonedDateTime.now(ZoneId.systemDefault()),
                booking.getStartTime().atZone(ZoneId.systemDefault())
        ).toMinutes();
        if (
                booking.getStatus().equals(AssessmentBookingStatusEnum.CONFIRMED.getCode())
                        &&
                        startInMinutes < 48 * 60
        ) {
            throw new BusinessException(ResponseEnum.BOOKING_CANCEL_TIME_LIMIT);
        }

        AssessmentSlot slot = assessmentSlotMapper.selectById(booking.getAssessmentSlotId());
        if (Objects.isNull(slot)) {
            throw new BusinessException(ResponseEnum.DATA_NOT_QUERIED.getCode(), "Assessment slot does not exist");
        }

        boolean isRemoved = this.remove(
                Wrappers.<AssessmentBooking>lambdaUpdate()
                        .eq(AssessmentBooking::getId, id)
                        .ne(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.COMPLETED.getCode())
        );
        if (!isRemoved) {
            throw new BusinessException(ResponseEnum.DATA_SAVE_FAILED.getCode(), "Cancel booking failed, this booking is already completed");
        }

        // No need to update optimistic lock version, so use updateWrapper to prevent auto-update
        LambdaUpdateWrapper<AssessmentSlot> slotUpdateWrapper = Wrappers.lambdaUpdate();
        slotUpdateWrapper.eq(AssessmentSlot::getId, slot.getId());
        slotUpdateWrapper.set(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.AVAILABLE.getCode());

        // Update repeat slot's next available time
        LocalDateTime nextAvailableTime = assessmentSlotServiceImpl.calculateRemainAvailableTime(slot);
        if (Objects.nonNull(nextAvailableTime) && slot.getNextAvailableTime().isAfter(nextAvailableTime)) {
            slotUpdateWrapper.set(AssessmentSlot::getNextAvailableTime, nextAvailableTime);
        }

        assessmentSlotMapper.update(slotUpdateWrapper);
        return true;
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

        vo.setSupervisorId(supervisor.getId());
        vo.setSupervisorName(supervisor.getName());
        vo.setWbaType(slot.getType());
        vo.setHospital(slot.getHospital());
        vo.setDepartment(slot.getDepartment());
        vo.setLocation(slot.getLocation());
        vo.setWbaType(slot.getType());
        vo.setStartTime(slot.getStartTime());
        vo.setEndTime(slot.getEndTime());
        // Calculate the duration of the slot
        String duration = Duration.between(slot.getStartTime().atZone(ZoneId.systemDefault()), slot.getEndTime().atZone(ZoneId.systemDefault())).toMinutes() + "min";
        vo.setDurationMinutes(duration);
        vo.setStatus(booking.getStatus());
        vo.setVersion(booking.getVersion());
        return vo;
    }
}
