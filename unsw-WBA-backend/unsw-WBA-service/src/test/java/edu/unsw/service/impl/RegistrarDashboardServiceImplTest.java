package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.annotation.JsonFormat;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.RegistrarDashboardVo;
import edu.unsw.domain.vo.WeeklyActivityVo;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarDashboardServiceImplTest {
    @Mock
    private AssessmentBookingMapper assessmentBookingMapper;

    @Mock
    private AssessmentSlotMapper assessmentSlotMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RegistrarDashboardServiceImpl registrarDashboardService;

    @BeforeAll
    static void initializeMybatisPlusLambdaMetadata() {
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, AssessmentSlot.class);
        TableInfoHelper.initTableInfo(builderAssistant, AssessmentBooking.class);
    }

    @Test
    void returnsEmptyDashboardWhenNoSlotsMatch() {
        when(assessmentSlotMapper.selectList(any())).thenReturn(List.of());

        RegistrarDashboardVo dashboard = registrarDashboardService.getDashboard(null, null, null);

        assertEquals(0L, dashboard.getSummary().getCompletedWbas());
        assertEquals(0L, dashboard.getSummary().getTotalBookings());
        assertEquals(0L, dashboard.getSummary().getAvailableSlots());
        assertEquals(0L, dashboard.getSummary().getActiveSupervisors());
        assertTrue(dashboard.getWeeklyActivity().isEmpty());
        assertTrue(dashboard.getWbaTypeBreakdown().isEmpty());
        assertTrue(dashboard.getSupervisorParticipation().isEmpty());
        verifyNoInteractions(assessmentBookingMapper, userMapper);
    }

    @Test
    void returnsIsoWeekStartDateWithoutChangingWeeklyCounts() {
        AssessmentSlot mondaySlot = slot(1L, 10L, LocalDateTime.of(2026, 7, 20, 9, 0));
        AssessmentSlot sundaySlot = slot(2L, 10L, LocalDateTime.of(2026, 7, 26, 15, 0));
        AssessmentBooking completed = booking(
                1L,
                LocalDateTime.of(2026, 7, 21, 10, 0),
                AssessmentBookingStatusEnum.COMPLETED.getCode()
        );
        AssessmentBooking confirmed = booking(
                2L,
                LocalDateTime.of(2026, 7, 26, 16, 0),
                AssessmentBookingStatusEnum.CONFIRMED.getCode()
        );

        when(assessmentSlotMapper.selectList(any())).thenReturn(List.of(mondaySlot, sundaySlot));
        when(assessmentBookingMapper.selectList(any())).thenReturn(List.of(completed, confirmed));

        User supervisor = new User();
        supervisor.setId(10L);
        supervisor.setName("Supervisor");
        when(userMapper.selectByIds(anyCollection())).thenReturn(List.of(supervisor));

        RegistrarDashboardVo dashboard = registrarDashboardService.getDashboard(null, null, null);

        assertEquals(1, dashboard.getWeeklyActivity().size());
        WeeklyActivityVo activity = dashboard.getWeeklyActivity().getFirst();
        assertEquals(LocalDate.of(2026, 7, 20), activity.getDate());
        assertEquals(2L, activity.getSlots());
        assertEquals(2L, activity.getBookings());
        assertEquals(1L, activity.getCompleted());
        assertEquals(2L, dashboard.getSummary().getTotalBookings());
        assertEquals(1L, dashboard.getSummary().getCompletedWbas());
    }

    @Test
    void keepsTheSameIsoWeekNumberFromDifferentYearsSeparateAndOrdered() {
        AssessmentSlot firstYear = slot(1L, 10L, LocalDateTime.of(2024, 1, 1, 9, 0));
        AssessmentSlot secondYear = slot(2L, 10L, LocalDateTime.of(2024, 12, 30, 9, 0));

        when(assessmentSlotMapper.selectList(any())).thenReturn(List.of(secondYear, firstYear));
        when(assessmentBookingMapper.selectList(any())).thenReturn(List.of());

        RegistrarDashboardVo dashboard = registrarDashboardService.getDashboard(null, null, null);

        assertEquals(
                List.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 30)),
                dashboard.getWeeklyActivity().stream().map(WeeklyActivityVo::getDate).toList()
        );
        assertEquals(List.of(1L, 1L),
                dashboard.getWeeklyActivity().stream().map(WeeklyActivityVo::getSlots).toList());
    }

    @Test
    void exposesAndSerializesDateOnlyFieldWithExplicitFormat() throws Exception {
        Field dateField = WeeklyActivityVo.class.getDeclaredField("date");
        JsonFormat jsonFormat = dateField.getAnnotation(JsonFormat.class);

        assertEquals(LocalDate.class, dateField.getType());
        assertNotNull(jsonFormat);
        assertEquals("yyyy-MM-dd", jsonFormat.pattern());
        assertThrows(NoSuchFieldException.class, () -> WeeklyActivityVo.class.getDeclaredField("week"));

        WeeklyActivityVo activity = new WeeklyActivityVo();
        activity.setDate(LocalDate.of(2026, 7, 20));
        String json = new JsonMapper().writeValueAsString(activity);
        assertTrue(json.contains("\"date\":\"2026-07-20\""));
        assertFalse(json.contains("\"week\""));
    }

    private AssessmentSlot slot(Long id, Long creator, LocalDateTime startTime) {
        AssessmentSlot slot = new AssessmentSlot();
        slot.setId(id);
        slot.setCreator(creator);
        slot.setStartTime(startTime);
        return slot;
    }

    private AssessmentBooking booking(Long slotId, LocalDateTime startTime, Integer status) {
        AssessmentBooking booking = new AssessmentBooking();
        booking.setAssessmentSlotId(slotId);
        booking.setStartTime(startTime);
        booking.setStatus(status);
        return booking;
    }
}
