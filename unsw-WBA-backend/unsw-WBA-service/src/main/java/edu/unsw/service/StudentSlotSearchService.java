package edu.unsw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import edu.unsw.service.enums.AssessmentSlotRepeatTypeEnum;
import edu.unsw.service.enums.AssessmentSlotStatusEnum;
import edu.unsw.service.utils.RruleUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Student search is paged after occurrence filtering, rather than before it. */
@Service
public class StudentSlotSearchService {
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 90;
    private final AssessmentSlotMapper slotMapper;
    private final AssessmentBookingMapper bookingMapper;
    private final UserMapper userMapper;
    private final Clock clock;

    public StudentSlotSearchService(AssessmentSlotMapper slotMapper, AssessmentBookingMapper bookingMapper,
                                    UserMapper userMapper, @Qualifier("bookingClock") Clock clock) {
        this.slotMapper = slotMapper;
        this.bookingMapper = bookingMapper;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    public Page<AssessmentSlotVo> search(AssessmentSlotQueryDto query) {
        validatePaging(query);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime requestedStart = query.getStartTime() == null ? now : query.getStartTime();
        LocalDateTime end = query.getEndTime() == null ? requestedStart.plusDays(DEFAULT_WINDOW_DAYS) : query.getEndTime();
        if (!requestedStart.isBefore(end)) {
            throw new BusinessException("Search start time must be before end time");
        }
        if (Duration.between(requestedStart, end).compareTo(Duration.ofDays(MAX_WINDOW_DAYS)) > 0) {
            throw new BusinessException("Search window cannot exceed 90 days");
        }
        LocalDateTime start = requestedStart.isBefore(now) ? now : requestedStart;
        Page<AssessmentSlotVo> page = new Page<>(query.getPageNum(), query.getPageQuantity());
        if (!start.isBefore(end)) {
            return page;
        }

        LambdaQueryWrapper<AssessmentSlot> filter = Wrappers.<AssessmentSlot>lambdaQuery()
                .eq(AssessmentSlot::getIsEnable, true)
                .eq(AssessmentSlot::getStatus, AssessmentSlotStatusEnum.AVAILABLE.getCode())
                .eq(query.getSupervisorId() != null, AssessmentSlot::getCreator, query.getSupervisorId())
                .eq(StringUtils.hasText(query.getHospital()), AssessmentSlot::getHospital, query.getHospital())
                .eq(StringUtils.hasText(query.getDepartment()), AssessmentSlot::getDepartment, query.getDepartment())
                .eq(StringUtils.hasText(query.getType()), AssessmentSlot::getType, query.getType());

        if (StringUtils.hasText(query.getSupervisorName())) {
            List<User> supervisors = userMapper.selectList(Wrappers.<User>lambdaQuery()
                    .like(User::getName, query.getSupervisorName().trim()).select(User::getId));
            if (supervisors.isEmpty()) {
                return page;
            }
            filter.in(AssessmentSlot::getCreator, supervisors.stream().map(User::getId).toList());
        }

        // A repeating slot's template may be in the past while its later sessions are still available.
        filter.and(window -> window
                .and(single -> single.eq(AssessmentSlot::getRepeatType, 0)
                        .ge(AssessmentSlot::getStartTime, start).le(AssessmentSlot::getEndTime, end))
                .or(repeated -> repeated.ne(AssessmentSlot::getRepeatType, 0)
                        .le(AssessmentSlot::getRepeatStartTime, end).ge(AssessmentSlot::getRepeatEndTime, start)));
        List<AssessmentSlot> slots = slotMapper.selectList(filter);
        if (slots.isEmpty()) {
            return page;
        }

        List<Long> slotIds = slots.stream().map(AssessmentSlot::getId).toList();
        List<AssessmentBooking> bookings = bookingMapper.selectList(Wrappers.<AssessmentBooking>lambdaQuery()
                .in(AssessmentBooking::getAssessmentSlotId, slotIds)
                .in(AssessmentBooking::getStatus, List.of(AssessmentBookingStatusEnum.PENDING.getCode(),
                        AssessmentBookingStatusEnum.CONFIRMED.getCode()))
                .ge(AssessmentBooking::getStartTime, start).le(AssessmentBooking::getStartTime, end)
                .select(AssessmentBooking::getAssessmentSlotId, AssessmentBooking::getStartTime));
        Map<Long, Set<LocalDateTime>> occupied = bookings.stream().collect(Collectors.groupingBy(
                AssessmentBooking::getAssessmentSlotId,
                Collectors.mapping(AssessmentBooking::getStartTime, Collectors.toSet())));

        List<Match> matches = new ArrayList<>();
        for (AssessmentSlot slot : slots) {
            List<RruleUtils.Occurrence> times = occurrences(slot, start, end).stream()
                    .filter(time -> matchesDailyConstraints(time, query))
                    .filter(time -> !occupied.getOrDefault(slot.getId(), Set.of()).contains(time.startTime()))
                    .sorted(Comparator.comparing(RruleUtils.Occurrence::startTime)).toList();
            if (!times.isEmpty()) {
                matches.add(new Match(slot, times));
            }
        }
        Comparator<Match> order = Boolean.TRUE.equals(query.getIsOrderByAvailableTime())
                ? Comparator.comparing((Match match) -> match.times().getFirst().startTime())
                    .thenComparing(match -> match.slot().getId())
                : Comparator.comparing((Match match) -> match.slot().getId()).reversed();
        matches.sort(order);
        page.setTotal(matches.size());
        long offset = query.getPageQuantity() == -1 ? 0L
                : ((long) query.getPageNum() - 1) * query.getPageQuantity();
        if (offset >= matches.size()) {
            return page;
        }
        int to = query.getPageQuantity() == -1 ? matches.size()
                : (int) Math.min(matches.size(), offset + query.getPageQuantity());
        List<Match> slice = matches.subList((int) offset, to);
        Set<Long> creatorIds = slice.stream().map(match -> match.slot().getCreator()).collect(Collectors.toSet());
        Map<Long, User> creators = userMapper.selectByIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        page.setRecords(slice.stream().map(match -> toView(match, creators.get(match.slot().getCreator()))).toList());
        return page;
    }

    private void validatePaging(AssessmentSlotQueryDto query) {
        if (query.getEarliestTime() != null && query.getLatestTime() != null
                && !query.getEarliestTime().isBefore(query.getLatestTime())) {
            throw new BusinessException("Daily start time must be before daily end time");
        }
        if (query.getWeekdays() != null && (query.getWeekdays().size() > 7
                || query.getWeekdays().stream().anyMatch(day -> day == null || day < 1 || day > 7))) {
            throw new BusinessException("Weekdays must be numbers from 1 (Monday) to 7 (Sunday)");
        }
        if (query.getPageNum() == null || query.getPageNum() < 1 || query.getPageQuantity() == null
                || (query.getPageQuantity() != -1 && query.getPageQuantity() < 1)) {
            throw new BusinessException("Page number and page size must be positive (or page size -1 for all)");
        }
    }

    private boolean matchesDailyConstraints(RruleUtils.Occurrence time, AssessmentSlotQueryDto query) {
        if (query.getWeekdays() != null && !query.getWeekdays().isEmpty()
                && !query.getWeekdays().contains(time.startTime().getDayOfWeek().getValue())) return false;
        if ((query.getEarliestTime() != null || query.getLatestTime() != null)
                && !time.startTime().toLocalDate().equals(time.endTime().toLocalDate())) return false;
        return (query.getEarliestTime() == null || !time.startTime().toLocalTime().isBefore(query.getEarliestTime()))
                && (query.getLatestTime() == null || !time.endTime().toLocalTime().isAfter(query.getLatestTime()));
    }

    private List<RruleUtils.Occurrence> occurrences(AssessmentSlot slot, LocalDateTime start, LocalDateTime end) {
        if (slot.getStartTime() == null || slot.getEndTime() == null || !slot.getStartTime().isBefore(slot.getEndTime())) {
            return List.of();
        }
        List<RruleUtils.Occurrence> times;
        if (Objects.equals(slot.getRepeatType(), AssessmentSlotRepeatTypeEnum.NO_REPEAT.getCode())) {
            times = List.of(new RruleUtils.Occurrence(slot.getStartTime(), slot.getEndTime()));
        } else {
            if (slot.getRepeatStartTime() == null || slot.getRepeatEndTime() == null || !StringUtils.hasText(slot.getRrule())) {
                return List.of();
            }
            LocalDateTime from = start.isAfter(slot.getRepeatStartTime()) ? start : slot.getRepeatStartTime();
            LocalDateTime until = end.isBefore(slot.getRepeatEndTime()) ? end : slot.getRepeatEndTime();
            if (!from.isBefore(until)) {
                return List.of();
            }
            times = RruleUtils.calculateOccurrences(slot.getRrule(), slot.getStartTime(), slot.getEndTime(), from, until);
        }
        return times.stream().filter(time -> !time.startTime().isBefore(start) && !time.endTime().isAfter(end)
                && (slot.getRepeatEndTime() == null || time.endTime().compareTo(slot.getRepeatEndTime()) <= 0)).toList();
    }

    private AssessmentSlotVo toView(Match match, User creator) {
        AssessmentSlot slot = match.slot();
        AssessmentSlotVo view = new AssessmentSlotVo();
        BeanUtils.copyProperties(slot, view);
        view.setNextAvailableTime(match.times().getFirst().startTime());
        view.setAvailableTimeList(match.times().stream().map(time -> List.of(time.startTime(), time.endTime())).toList());
        view.setDuration(Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes() + "min");
        view.setTimeZone(clock.getZone().getId());
        if (creator != null) {
            view.setCreatorName(creator.getName());
            view.setCreatorAvatar(creator.getAvatar());
        }
        RruleUtils.RruleDisplayDto rule = RruleUtils.parseRrule(slot.getRrule());
        view.setInterval(rule.interval());
        view.setDaysOfWeek(rule.daysOfWeek());
        view.setDaysOfMonth(rule.daysOfMonth());
        view.setMonths(rule.months());
        return view;
    }

    private record Match(AssessmentSlot slot, List<RruleUtils.Occurrence> times) { }
}
