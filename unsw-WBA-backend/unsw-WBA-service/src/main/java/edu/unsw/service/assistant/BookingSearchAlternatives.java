package edu.unsw.service.assistant;

import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import org.springframework.beans.BeanUtils;
import java.time.*;
import java.util.*;

/** Finite, explicit changes to one preference dimension. Clinical type and placement never change. */
final class BookingSearchAlternatives {
    record Option(String kind,String title,String explanation,AssessmentSlotQueryDto conditions) { }

    static List<Option> options(AssessmentSlotQueryDto source) {
        var result=new ArrayList<Option>();
        var date=copy(source);
        date.setStartTime(source.getStartTime().plusWeeks(1)); date.setEndTime(source.getEndTime().plusWeeks(1));
        result.add(new Option("DATE","Same search window, one week later","Changes the dates only. Daily time and weekday restrictions stay the same.",date));

        boolean daily=source.getEarliestTime()!=null || source.getLatestTime()!=null;
        boolean shortWindow=Duration.between(source.getStartTime(),source.getEndTime()).compareTo(Duration.ofDays(1))<=0;
        boolean boundedTime=!source.getStartTime().toLocalTime().equals(LocalTime.MIDNIGHT) || !source.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT);
        if(daily || (shortWindow && boundedTime)) {
            var time=copy(source);
            var lastDay=source.getEndTime().toLocalDate();
            if(source.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT)) lastDay=lastDay.minusDays(1);
            time.setStartTime(source.getStartTime().toLocalDate().atStartOfDay()); time.setEndTime(lastDay.plusDays(1).atStartOfDay());
            time.setEarliestTime(null); time.setLatestTime(null);
            result.add(new Option("TIME","Other times on the requested dates","Removes the time-of-day restriction. The calendar dates, weekdays and supervisor stay the same.",time));
        }
        if(source.getSupervisorId()!=null || source.getSupervisorName()!=null) {
            var mentor=copy(source); mentor.setSupervisorId(null); mentor.setSupervisorName(null);
            result.add(new Option("SUPERVISOR","A different supervisor","Requires accepting another supervisor. Dates, daily times and weekdays stay the same.",mentor));
        }
        if(source.getWeekdays()!=null && !source.getWeekdays().isEmpty()) {
            var weekdays=copy(source); weekdays.setWeekdays(List.of());
            result.add(new Option("WEEKDAYS","Other weekdays within the date window","Removes the weekday restriction. Date bounds, daily times and supervisor stay the same.",weekdays));
        }
        return result;
    }

    private static AssessmentSlotQueryDto copy(AssessmentSlotQueryDto source) {
        var result=new AssessmentSlotQueryDto(); BeanUtils.copyProperties(source,result);
        result.setWeekdays(source.getWeekdays()==null?List.of():List.copyOf(source.getWeekdays()));
        result.setPageNum(1); result.setPageQuantity(-1); result.setIsOrderByAvailableTime(true);
        return result;
    }
}
