package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.AssessmentSlotSaveDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.UpcomingSessionVo;
import edu.unsw.domain.dto.AssessmentCompletionDto;
import edu.unsw.service.AssessmentSlotService;
import edu.unsw.service.SupervisorBookingService;
import edu.unsw.domain.vo.SupervisorBookingResult;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import edu.unsw.service.enums.AssessmentSlotRepeatTypeEnum;
import edu.unsw.service.enums.AssessmentSlotStatusEnum;
import edu.unsw.service.utils.RruleUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 00:42
 * @description AssessmentSlot services implementation
 */
@Service
@Slf4j
public class AssessmentSlotServiceImpl extends ServiceImpl<AssessmentSlotMapper, AssessmentSlot> implements AssessmentSlotService {
    private final UserMapper userMapper;
    private final AssessmentBookingMapper assessmentBookingMapper;
    private final RedissonUtils redissonUtils;
    private final SupervisorBookingService supervisorBookings;

    public AssessmentSlotServiceImpl(UserMapper userMapper, AssessmentBookingMapper assessmentBookingMapper, RedissonUtils redissonUtils, SupervisorBookingService supervisorBookings) {
        this.userMapper = userMapper;
        this.assessmentBookingMapper = assessmentBookingMapper;
        this.redissonUtils = redissonUtils;
        this.supervisorBookings = supervisorBookings;
    }

    @Override
    public Page<AssessmentSlotVo> getAssessmentSlotList(AssessmentSlotQueryDto assessmentSlotQueryDto) {
        Long supervisorId = assessmentSlotQueryDto.getSupervisorId();
        return getAssessmentSlotVoPage(assessmentSlotQueryDto,
                queryWrapper ->
                        queryWrapper
                                .eq(Objects.nonNull(supervisorId), AssessmentSlot::getCreator, supervisorId)
                                .ne(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.COMPLETED.getCode())
        );
    }

    Page<AssessmentSlotVo> getAssessmentSlotVoPage(AssessmentSlotQueryDto assessmentSlotQueryDto, Consumer<LambdaQueryWrapper<AssessmentSlot>> conditionBuilder) {
        String hospital = assessmentSlotQueryDto.getHospital();
        String department = assessmentSlotQueryDto.getDepartment();
        String type = assessmentSlotQueryDto.getType();

        LambdaQueryWrapper<AssessmentSlot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(StringUtils.hasText(hospital), AssessmentSlot::getHospital, hospital)
                .eq(StringUtils.hasText(department), AssessmentSlot::getDepartment, department)
                .eq(StringUtils.hasText(type), AssessmentSlot::getType, type);

        // order by next available time
        if (Boolean.TRUE.equals(assessmentSlotQueryDto.getIsOrderByAvailableTime())) {
            queryWrapper.orderByAsc(AssessmentSlot::getNextAvailableTime);
        } else {
            queryWrapper.orderByDesc(AssessmentSlot::getId);
        }

        // Concatenate the passed query conditions
        if (Objects.nonNull(conditionBuilder)) {
            conditionBuilder.accept(queryWrapper);
        }

        Page<AssessmentSlot> assessmentSlotPage = this.page(new Page<>(assessmentSlotQueryDto.getPageNum(), assessmentSlotQueryDto.getPageQuantity()), queryWrapper);

        Page<AssessmentSlotVo> slotVoPage = new Page<>();
        BeanUtils.copyProperties(assessmentSlotPage, slotVoPage);

        List<AssessmentSlot> assessmentSlotList = assessmentSlotPage.getRecords();
        if (assessmentSlotList.isEmpty()) {
            return slotVoPage;
        }

        // Get the creator of the assessment slot
        List<Long> creatorIdList = assessmentSlotList.stream().map(AssessmentSlot::getCreator).toList();
        List<User> creatorList = userMapper.selectByIds(creatorIdList);

        List<AssessmentSlotVo> assessmentSlotVoList = assessmentSlotList.stream()
                .map(item -> {
                    AssessmentSlotVo assessmentSlotVo = new AssessmentSlotVo();
                    BeanUtils.copyProperties(item, assessmentSlotVo);

                    // Calculate the duration of the assessment slot
                    LocalDateTime startTime = item.getStartTime();
                    LocalDateTime endTime = item.getEndTime();
                    String duration = Duration.between(startTime.atZone(ZoneId.systemDefault()), endTime.atZone(ZoneId.systemDefault())).toMinutes() + "min";
                    assessmentSlotVo.setDuration(duration);

                    // Set the creator's id and name of the assessment slot
                    User creator = creatorList.stream().filter(user -> user.getId().equals(item.getCreator())).findFirst().orElse(new User());
                    assessmentSlotVo.setCreator(creator.getId());
                    assessmentSlotVo.setCreatorName(creator.getName());
                    assessmentSlotVo.setCreatorAvatar(creator.getAvatar());

                    // Set repeat rule
                    String rrule = item.getRrule();
                    RruleUtils.RruleDisplayDto rruleDisplayDto = RruleUtils.parseRrule(rrule);
                    assessmentSlotVo.setRepeatType(AssessmentSlotRepeatTypeEnum.getEnum(rruleDisplayDto.repeatTypeName()).getCode());
                    assessmentSlotVo.setInterval(rruleDisplayDto.interval());
                    assessmentSlotVo.setDaysOfWeek(rruleDisplayDto.daysOfWeek());
                    assessmentSlotVo.setDaysOfMonth(rruleDisplayDto.daysOfMonth());
                    assessmentSlotVo.setMonths(rruleDisplayDto.months());

                    // Calculate availableTimeList
                    List<RruleUtils.Occurrence> occurrences = calculateRemainAvailableTimeList(item);
                    List<List<LocalDateTime>> availableTimeList = new ArrayList<>();
                    occurrences.forEach(time -> {
                        List<LocalDateTime> singleAvailableTimeList = new ArrayList<>();
                        singleAvailableTimeList.add(time.startTime());
                        singleAvailableTimeList.add(time.endTime());
                        availableTimeList.add(singleAvailableTimeList);
                    });
                    assessmentSlotVo.setAvailableTimeList(availableTimeList);

                    return assessmentSlotVo;
                })
                .toList();

        slotVoPage.setRecords(assessmentSlotVoList);
        return slotVoPage;
    }

    List<RruleUtils.Occurrence> calculateRemainAvailableTimeList(AssessmentSlot slot) {
        if (Objects.equals(AssessmentSlotRepeatTypeEnum.NO_REPEAT.getCode(), slot.getRepeatType())) {
            RruleUtils.Occurrence occurrence = new RruleUtils.Occurrence(slot.getStartTime(), slot.getEndTime());
            return List.of(occurrence);
        }

        LocalDateTime startTime = slot.getStartTime();
        LocalDateTime endTime = slot.getEndTime();
        LocalDate firstDate = startTime.toLocalDate();
        LocalTime firstStartTime = startTime.toLocalTime();
        LocalTime firstEndTime = endTime.toLocalTime();
        List<RruleUtils.Occurrence> occurrences = RruleUtils.calculateOccurrences(slot.getRrule(), firstDate.atTime(firstStartTime), firstDate.atTime(firstEndTime), slot.getRepeatStartTime(), slot.getRepeatEndTime());
        List<AssessmentBooking> assessmentBookingList = assessmentBookingMapper.selectList(
                Wrappers.<AssessmentBooking>lambdaQuery()
                        .eq(AssessmentBooking::getAssessmentSlotId, slot.getId())
                        .and(i -> i
                                .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.PENDING.getCode())
                                .or()
                                .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.CONFIRMED.getCode())
                        )
                        .select(AssessmentBooking::getStartTime)
        );
        occurrences.removeIf(occurrence -> assessmentBookingList.stream().anyMatch(booking -> booking.getStartTime().equals(occurrence.startTime())));
        return occurrences;
    }

    LocalDateTime calculateRemainAvailableTime(AssessmentSlot slot) {
        if (Objects.isNull(slot)) {
            throw new BusinessException(ResponseEnum.SLOT_NOT_EXIST);
        }
        List<RruleUtils.Occurrence> occurrences = calculateRemainAvailableTimeList(slot);
        if (occurrences.isEmpty()) {
            return null;
        }
        return occurrences.getFirst().startTime();
    }

    @Override
    public AssessmentSlotVo saveAssessmentSlot(AssessmentSlotSaveDto assessmentSlotSaveDto) {
        String lockKey = RedisKeyConstant.SLOT_LOCK_PREFIX;
        Long id = assessmentSlotSaveDto.getId();
        // Need to add lock when updating slot to prevent new bookings
        if (Objects.nonNull(id)) {
            lockKey += assessmentSlotSaveDto;
            boolean isLocked = redissonUtils.tryLock(lockKey, 4, 3);
            if (!isLocked) {
                throw new BusinessException(ResponseEnum.LOCK_CONTROL);
            }
        }

        try {
            // Not permitted to edit slot when there are any unprocessed bookings
            if (Objects.nonNull(id)) {
                // Check if there are any unprocessed bookings for the assessment slot
                checkUnprocessedBookings(id);
            }

            // Check start and end time
            LocalDateTime startTime = assessmentSlotSaveDto.getStartTime();
            LocalDateTime endTime = assessmentSlotSaveDto.getEndTime();
            if (startTime.isAfter(endTime) || startTime.isEqual(endTime) || endTime.isBefore(LocalDateTime.now(ZoneId.systemDefault()))) {
                throw new BusinessException(ResponseEnum.TIME_ILLEGAL);
            }

            AssessmentSlot assessmentSlot = new AssessmentSlot();
            BeanUtils.copyProperties(assessmentSlotSaveDto, assessmentSlot);

            // Get the current user (creator of the assessment slot)
            CurrentUser user = CurrentUserUtils.getUser();
            assessmentSlot.setCreator(user.getId());
            assessmentSlot.setNextAvailableTime(assessmentSlot.getStartTime());

            if (!Objects.equals(AssessmentSlotRepeatTypeEnum.NO_REPEAT.getCode(), assessmentSlotSaveDto.getRepeatType())) {
                // Generate rrule
                String rrule = generateRrule(assessmentSlotSaveDto);
                assessmentSlot.setRrule(rrule);

                // Calculate the next available time
                LocalDate firstDate = startTime.toLocalDate();
                LocalTime firstStartTime = startTime.toLocalTime();
                LocalTime firstEndTime = endTime.toLocalTime();
                List<RruleUtils.Occurrence> occurrences = RruleUtils.calculateOccurrences(rrule, firstDate.atTime(firstStartTime), firstDate.atTime(firstEndTime), assessmentSlot.getRepeatStartTime(), assessmentSlot.getRepeatEndTime());
                if (!occurrences.isEmpty()) {
                    assessmentSlot.setNextAvailableTime(occurrences.getFirst().startTime());
                }
            }

            this.saveOrUpdate(assessmentSlot);

            AssessmentSlotVo assessmentSlotVo = new AssessmentSlotVo();
            BeanUtils.copyProperties(assessmentSlot, assessmentSlotVo);
            assessmentSlotVo.setCreatorName(user.getName());
            // Calculate the duration of the assessment slot
            String duration = Duration.between(startTime.atZone(ZoneId.systemDefault()), endTime.atZone(ZoneId.systemDefault())).toMinutes() + "min";
            assessmentSlotVo.setDuration(duration);

            return assessmentSlotVo;
        } finally {
            if (Objects.nonNull(id)) {
                redissonUtils.unlock(lockKey);
            }
        }
    }

    private String generateRrule(AssessmentSlotSaveDto assessmentSlotSaveDto) {
        AssessmentSlotRepeatTypeEnum asTypeEnum = AssessmentSlotRepeatTypeEnum.getEnum(assessmentSlotSaveDto.getRepeatType());
        return switch (asTypeEnum) {
            case AssessmentSlotRepeatTypeEnum.DAILY -> RruleUtils.createDailyRule(assessmentSlotSaveDto.getInterval());
            case AssessmentSlotRepeatTypeEnum.WEEKLY ->
                    RruleUtils.createWeeklyRule(assessmentSlotSaveDto.getInterval(), assessmentSlotSaveDto.getDaysOfWeek());
            case AssessmentSlotRepeatTypeEnum.MONTHLY ->
                    RruleUtils.createMonthlyRule(assessmentSlotSaveDto.getInterval(), assessmentSlotSaveDto.getDaysOfMonth());
            case AssessmentSlotRepeatTypeEnum.YEARLY ->
                    RruleUtils.createYearlyRule(assessmentSlotSaveDto.getInterval(), assessmentSlotSaveDto.getMonths(), assessmentSlotSaveDto.getDaysOfMonth());
            default -> "";
        };
    }

    @Override
    public Boolean toggleAssessmentSlotsEnableDisable(List<Long> ids, Boolean isEnable) {
        return this.update(
                Wrappers.<AssessmentSlot>lambdaUpdate()
                        .in(AssessmentSlot::getId, ids)
                        .set(AssessmentSlot::getIsEnable, isEnable)
        );
    }

    @Override
    public Boolean delAssessmentSlot(Long id) {
        String lockKey = RedisKeyConstant.SLOT_LOCK_PREFIX + id;
        boolean isLocked = redissonUtils.tryLock(lockKey, 4, 3);
        if (!isLocked) {
            throw new BusinessException(ResponseEnum.LOCK_CONTROL);
        }

        try {
            // Check if there are any unprocessed bookings for the assessment slot
            checkUnprocessedBookings(id);

            boolean isRemoved = this.remove(
                    Wrappers.<AssessmentSlot>lambdaQuery()
                            .eq(AssessmentSlot::getId, id)
                            .ne(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.COMPLETED.getCode())
            );
            if (!isRemoved) {
                throw new BusinessException("Cannot delete a completed assessment slot");
            }

            return true;
        } finally {
            redissonUtils.unlock(lockKey);
        }
    }

    /**
     * Check if there are any unprocessed bookings for the assessment slot
     * @param slotId slot id
     */
    private void checkUnprocessedBookings(Long slotId) {
        Long l = assessmentBookingMapper.selectCount(
                Wrappers.<AssessmentBooking>lambdaQuery()
                        .eq(AssessmentBooking::getAssessmentSlotId, slotId)
                        .and(i -> i
                                .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.PENDING.getCode())
                                .or()
                                .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.CONFIRMED.getCode())
                        )
        );
        if (l > 0) {
            throw new BusinessException(ResponseEnum.BOOKING_CONFLICT);
        }
    }

    @Override
    public Page<UpcomingSessionVo> getUpcomingSession(AssessmentSlotQueryDto query) {
        return supervisorBookings.sessions(query,0);
    }

    @Override
    public Page<UpcomingSessionVo> getApprovedSession(AssessmentSlotQueryDto query) {
        return supervisorBookings.sessions(query,1);
    }

    @Override
    public SupervisorBookingResult approveSlot(Long id,Long bookingId,Integer version) {
        return supervisorBookings.decide(SupervisorBookingService.Action.APPROVE,id,bookingId,version,null);
    }

    @Override
    public SupervisorBookingResult declineSlot(Long id,Long bookingId,Boolean isEnable,Integer version) {
        return supervisorBookings.decide(SupervisorBookingService.Action.DECLINE,id,bookingId,version,isEnable);
    }

    @Override
    public SupervisorBookingResult cancelSlot(Long id,Long bookingId,Boolean isEnable,Integer version) {
        return supervisorBookings.decide(SupervisorBookingService.Action.CANCEL,id,bookingId,version,isEnable);
    }

    @Override
    public SupervisorBookingResult markAssessmentCompleted(AssessmentCompletionDto request) {
        return supervisorBookings.complete(request);
    }

    @Override
    public SupervisorBookingResult updateAssessmentResult(AssessmentCompletionDto request) {
        return supervisorBookings.correct(request);
    }
}
