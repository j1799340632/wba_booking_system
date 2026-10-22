package edu.unsw.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.*;
import edu.unsw.service.RegistrarDashboardService;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import edu.unsw.service.enums.AssessmentSlotStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:57
 * @description Registrar dashboard-related services implementation
 */
@Service
public class RegistrarDashboardServiceImpl implements RegistrarDashboardService {
    private static final String UNKNOWN_TYPE = "Unknown";
    private static final String UNKNOWN_SUPERVISOR = "Unknown Supervisor";

    private final AssessmentBookingMapper assessmentBookingMapper;
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final UserMapper userMapper;

    public RegistrarDashboardServiceImpl(AssessmentBookingMapper assessmentBookingMapper,
                                         AssessmentSlotMapper assessmentSlotMapper,
                                         UserMapper userMapper) {
        this.assessmentBookingMapper = assessmentBookingMapper;
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.userMapper = userMapper;
    }

    @Override
    public RegistrarDashboardVo getDashboard(String hospital, String department, Long supervisorId) {
        List<AssessmentSlot> filteredSlots = queryFilteredSlots(hospital, department, supervisorId);
        if (filteredSlots.isEmpty()) {
            return buildEmptyDashboard();
        }

        List<Long> slotIds = filteredSlots.stream()
                .map(AssessmentSlot::getId)
                .filter(Objects::nonNull)
                .toList();

        List<AssessmentBooking> filteredBookings = new LambdaQueryChainWrapper<>(assessmentBookingMapper)
                .in(AssessmentBooking::getAssessmentSlotId, slotIds)
                .list();

        Map<Long, AssessmentSlot> slotMap = filteredSlots.stream()
                .collect(Collectors.toMap(AssessmentSlot::getId, slot -> slot, (a, b) -> a));

        RegistrarDashboardVo dashboardVo = new RegistrarDashboardVo();
        dashboardVo.setSummary(buildSummary(filteredSlots, filteredBookings));
        dashboardVo.setWeeklyActivity(buildWeeklyActivity(filteredSlots, filteredBookings));
        dashboardVo.setWbaTypeBreakdown(buildWbaTypeBreakdown(filteredBookings, slotMap));
        dashboardVo.setSupervisorParticipation(buildSupervisorParticipation(filteredBookings, slotMap));
        return dashboardVo;
    }

    private List<AssessmentSlot> queryFilteredSlots(String hospital, String department, Long supervisorId) {
        return new LambdaQueryChainWrapper<>(assessmentSlotMapper)
                .eq(StringUtils.hasText(hospital), AssessmentSlot::getHospital, hospital)
                .eq(StringUtils.hasText(department), AssessmentSlot::getDepartment, department)
                .eq(Objects.nonNull(supervisorId), AssessmentSlot::getCreator, supervisorId)
                .list();
    }

    private RegistrarDashboardVo buildEmptyDashboard() {
        DashboardSummaryVo summary = new DashboardSummaryVo();
        summary.setCompletedWbas(0L);
        summary.setTotalBookings(0L);
        summary.setAvailableSlots(0L);
        summary.setActiveSupervisors(0L);

        RegistrarDashboardVo dashboardVo = new RegistrarDashboardVo();
        dashboardVo.setSummary(summary);
        dashboardVo.setWeeklyActivity(Collections.emptyList());
        dashboardVo.setWbaTypeBreakdown(Collections.emptyList());
        dashboardVo.setSupervisorParticipation(Collections.emptyList());
        return dashboardVo;
    }

    private DashboardSummaryVo buildSummary(List<AssessmentSlot> filteredSlots,
                                            List<AssessmentBooking> filteredBookings) {
        long completedWbas = filteredBookings.stream()
                .filter(booking -> Objects.equals(booking.getStatus(), AssessmentBookingStatusEnum.COMPLETED.getCode()))
                .count();

        long availableSlots = filteredSlots.stream()
                .filter(slot -> Objects.equals(slot.getStatus(), AssessmentSlotStatusEnum.AVAILABLE.getCode()))
                .count();

        long activeSupervisors = filteredSlots.stream()
                .map(AssessmentSlot::getCreator)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        DashboardSummaryVo summary = new DashboardSummaryVo();
        summary.setCompletedWbas(completedWbas);
        summary.setTotalBookings((long) filteredBookings.size());
        summary.setAvailableSlots(availableSlots);
        summary.setActiveSupervisors(activeSupervisors);
        return summary;
    }

    private List<WeeklyActivityVo> buildWeeklyActivity(List<AssessmentSlot> filteredSlots,
                                                       List<AssessmentBooking> filteredBookings) {
        Map<LocalDate, Long> slotsByWeekStart = countByWeekStart(filteredSlots.stream()
                .map(AssessmentSlot::getStartTime)
                .filter(Objects::nonNull)
                .toList());

        Map<LocalDate, Long> bookingsByWeekStart = countByWeekStart(filteredBookings.stream()
                .map(AssessmentBooking::getStartTime)
                .filter(Objects::nonNull)
                .toList());

        Map<LocalDate, Long> completedByWeekStart = countByWeekStart(filteredBookings.stream()
                .filter(booking -> Objects.equals(booking.getStatus(), AssessmentBookingStatusEnum.COMPLETED.getCode()))
                .map(AssessmentBooking::getStartTime)
                .filter(Objects::nonNull)
                .toList());

        Set<LocalDate> allWeekStarts = new TreeSet<>();
        allWeekStarts.addAll(slotsByWeekStart.keySet());
        allWeekStarts.addAll(bookingsByWeekStart.keySet());
        allWeekStarts.addAll(completedByWeekStart.keySet());

        List<WeeklyActivityVo> weeklyActivity = new ArrayList<>(allWeekStarts.size());
        for (LocalDate weekStart : allWeekStarts) {
            WeeklyActivityVo weeklyActivityVo = new WeeklyActivityVo();
            weeklyActivityVo.setDate(weekStart);
            weeklyActivityVo.setSlots(slotsByWeekStart.getOrDefault(weekStart, 0L));
            weeklyActivityVo.setBookings(bookingsByWeekStart.getOrDefault(weekStart, 0L));
            weeklyActivityVo.setCompleted(completedByWeekStart.getOrDefault(weekStart, 0L));
            weeklyActivity.add(weeklyActivityVo);
        }
        return weeklyActivity;
    }

    private Map<LocalDate, Long> countByWeekStart(List<LocalDateTime> dateTimes) {
        return dateTimes.stream()
                .collect(Collectors.groupingBy(
                        dateTime -> dateTime.toLocalDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        Collectors.counting()
                ));
    }

    private List<WbaTypeBreakdownVo> buildWbaTypeBreakdown(List<AssessmentBooking> filteredBookings, Map<Long, AssessmentSlot> slotMap) {
        long totalBookings = filteredBookings.size();
        if (totalBookings == 0) {
            return Collections.emptyList();
        }

        Map<String, Long> typeCounts = filteredBookings.stream()
                .collect(Collectors.groupingBy(i -> resolveBookingType(i, slotMap), Collectors.counting()));

        return typeCounts.entrySet().stream()
                .map(entry -> {
                    WbaTypeBreakdownVo breakdownVo = new WbaTypeBreakdownVo();
                    breakdownVo.setType(entry.getKey());
                    breakdownVo.setCount(entry.getValue());
                    breakdownVo.setPercentage(entry.getValue() * 100.0 / totalBookings);
                    return breakdownVo;
                })
                .sorted(Comparator.comparing(WbaTypeBreakdownVo::getCount).reversed())
                .toList();
    }

    private List<SupervisorParticipationVo> buildSupervisorParticipation(List<AssessmentBooking> filteredBookings,
                                                                         Map<Long, AssessmentSlot> slotMap) {
        Map<Long, Long> supervisorCounts = new HashMap<>();
        for (AssessmentBooking booking : filteredBookings) {
            AssessmentSlot slot = slotMap.get(booking.getAssessmentSlotId());
            if (Objects.isNull(slot) || Objects.isNull(slot.getCreator())) {
                continue;
            }
            supervisorCounts.merge(slot.getCreator(), 1L, Long::sum);
        }

        if (supervisorCounts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> supervisorIds = new ArrayList<>(supervisorCounts.keySet());
        List<User> supervisors = userMapper.selectByIds(supervisorIds);
        Map<Long, User> supervisorMap = supervisors.stream()
                .filter(user -> Objects.nonNull(user.getId()))
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        return supervisorCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(entry -> {
                    SupervisorParticipationVo participationVo = new SupervisorParticipationVo();
                    participationVo.setSupervisorId(entry.getKey());
                    participationVo.setCount(entry.getValue());

                    User supervisor = supervisorMap.get(entry.getKey());
                    if (Objects.nonNull(supervisor) && StringUtils.hasText(supervisor.getName())) {
                        participationVo.setSupervisorName(supervisor.getName());
                    } else {
                        participationVo.setSupervisorName(UNKNOWN_SUPERVISOR);
                    }
                    return participationVo;
                })
                .toList();
    }

    private String resolveBookingType(AssessmentBooking booking, Map<Long, AssessmentSlot> slotMap) {
        AssessmentSlot slot = slotMap.get(booking.getAssessmentSlotId());
        if (!StringUtils.hasText(slot.getType())) {
            return UNKNOWN_TYPE;
        }
        return slot.getType();
    }
}
