package edu.unsw.service.assistant;

import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingSearchAlternativesTest {
    @Test void changesOnlyOneDimensionAndNeverAltersTypeHospitalDepartmentOrOriginal() {
        var query=query(); var options=BookingSearchAlternatives.options(query);
        assertEquals(List.of("DATE","TIME","SUPERVISOR","WEEKDAYS"),options.stream().map(o->o.kind()).toList());
        for(var option:options) {
            var q=option.conditions(); assertEquals("Mini-CEX",q.getType()); assertEquals("Hospital A",q.getHospital()); assertEquals("Medicine",q.getDepartment());
            if(!option.kind().equals("DATE") && !option.kind().equals("TIME")) {assertEquals(query.getStartTime(),q.getStartTime());assertEquals(query.getEndTime(),q.getEndTime());}
            if(!option.kind().equals("TIME")) {assertEquals(query.getEarliestTime(),q.getEarliestTime());assertEquals(query.getLatestTime(),q.getLatestTime());}
            if(!option.kind().equals("SUPERVISOR")) {assertEquals(10L,q.getSupervisorId());assertEquals("Dr Alpha",q.getSupervisorName());}
            if(!option.kind().equals("WEEKDAYS")) assertEquals(List.of(2),q.getWeekdays());
        }
        assertEquals(LocalDateTime.of(2026,10,27,12,0),query.getStartTime());assertEquals(10L,query.getSupervisorId());assertEquals(List.of(2),query.getWeekdays());
    }
    @Test void weekShiftPreservesWallTimeAcrossSydneyDstAndKeepsDuration() {
        var query=query();query.setStartTime(LocalDateTime.of(2027,3,30,14,0));query.setEndTime(query.getStartTime().plusHours(3));
        var shifted=BookingSearchAlternatives.options(query).getFirst().conditions();
        assertEquals(LocalDateTime.of(2027,4,6,14,0),shifted.getStartTime());assertEquals(shifted.getStartTime().plusHours(3),shifted.getEndTime());
    }
    @Test void timeRelaxationKeepsTheSameCalendarDatesAndExclusiveMidnightEnd() {
        var query=query();query.setStartTime(LocalDateTime.of(2026,10,26,0,0));query.setEndTime(LocalDateTime.of(2026,11,2,0,0));
        var time=BookingSearchAlternatives.options(query).stream().filter(o->o.kind().equals("TIME")).findFirst().orElseThrow().conditions();
        assertEquals(query.getStartTime(),time.getStartTime());assertEquals(query.getEndTime(),time.getEndTime());assertNull(time.getEarliestTime());assertNull(time.getLatestTime());
    }
    @Test void defaultThirtyDaySearchDoesNotInventDailyRestrictionsToRelax() {
        var query=new AssessmentSlotQueryDto();query.setStartTime(LocalDateTime.of(2026,10,26,15,30));query.setEndTime(query.getStartTime().plusDays(30));
        assertEquals(List.of("DATE"),BookingSearchAlternatives.options(query).stream().map(o->o.kind()).toList());
    }
    private AssessmentSlotQueryDto query() {
        var q=new AssessmentSlotQueryDto();q.setHospital("Hospital A");q.setDepartment("Medicine");q.setType("Mini-CEX");q.setSupervisorId(10L);q.setSupervisorName("Dr Alpha");
        q.setStartTime(LocalDateTime.of(2026,10,27,12,0));q.setEndTime(LocalDateTime.of(2026,10,27,18,0));q.setEarliestTime(LocalTime.NOON);q.setLatestTime(LocalTime.of(18,0));q.setWeekdays(new ArrayList<>(List.of(2)));return q;
    }
}
