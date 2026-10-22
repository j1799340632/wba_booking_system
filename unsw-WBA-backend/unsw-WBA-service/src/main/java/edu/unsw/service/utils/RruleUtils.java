package edu.unsw.service.utils;

import net.fortuna.ical4j.model.Month;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 19/7/2026 19:50
 * @description Parse or generate rrule i-Calendar (RFC 5545)
 */
public class RruleUtils {
    private RruleUtils() {
        /* This utility class should not be instantiated */
    }

    private static final Logger logger = LoggerFactory.getLogger(RruleUtils.class);

    /**
     * Generate a "daily repeating" RRULE
     */
    public static String createDailyRule(int interval) {
        return String.format("FREQ=DAILY;INTERVAL=%d", interval);
    }

    /**
     * Generate RRULEs for "day of the week", "day of the week every two weeks", or "day of the week every three weeks"
     *
     * @param interval Interval weeks: 1 represents weekly, 2 represents bi-weekly
     * @param daysOfWeek A list of abbreviations for days of the week. ["MO", "TU", "WE", "TH", "FR", "SA", "SU"].
     */
    public static String createWeeklyRule(int interval, List<String> daysOfWeek) {
        String byDay = String.join(",", daysOfWeek);
        return String.format("FREQ=WEEKLY;INTERVAL=%d;BYDAY=%s", interval, byDay);
    }

    /**
     * Generate an RRULE that repeats every few months (you can specify which days of the month)
     *
     * @param interval     Interval in months (e.g., 3 represents every 3 months)
     * @param daysOfMonth  The specific date of the month, such as [15] representing the 15th of that month. If null or empty is passed, the default date is the first day of the month
     */
    public static String createMonthlyRule(int interval, List<Integer> daysOfMonth) {
        if (daysOfMonth == null || daysOfMonth.isEmpty()) {
            return String.format("FREQ=MONTHLY;INTERVAL=%d", interval);
        }
        String byMonthDay = daysOfMonth.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return String.format("FREQ=MONTHLY;INTERVAL=%d;BYMONTHDAY=%s", interval, byMonthDay);
    }

    /**
     * Generate an RRULE that repeats annually
     *
     * @param months The specified list of months (1-12), such as [10] representing October
     * @param daysOfMonth The specific date of each month (1-31), such as [1, 2] representing the 1st and 2nd
     */
    public static String createYearlyRule(int interval, List<Integer> months, List<Integer> daysOfMonth) {
        String byMonth = months.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String byMonthDay = daysOfMonth.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return String.format("FREQ=YEARLY;INTERVAL=%d;BYMONTH=%s;BYMONTHDAY=%s", interval, byMonth, byMonthDay);
    }

    /**
     * Calculate repeating sequences
     *
     * @param rruleStr    RRULE string
     * @param startFirst  The first start time of the event (LocalDateTime)
     * @param endFirst    The first end time of the event (LocalDateTime)
     * @param rangeStart  Effective start time of the event
     * @param rangeEnd    Effective end time of the event
     * @return List of specific time periods during which the event will be executed
     */
    public static List<Occurrence> calculateOccurrences(
            String rruleStr,
            LocalDateTime startFirst,
            LocalDateTime endFirst,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        List<Occurrence> occurrences = new ArrayList<>();

        // If there is no duplicate rule, directly determine whether the first event is within the query range
        if (rruleStr == null || rruleStr.trim().isEmpty()) {
            if (!startFirst.isAfter(rangeEnd) && !endFirst.isBefore(rangeStart)) {
                occurrences.add(new Occurrence(startFirst, endFirst));
            }
            return occurrences;
        }

        try {
            VEvent vEvent = new VEvent(startFirst, endFirst, "AssessmentSlotEvent");

            // Parse and add repeating rule attributes
            RRule<LocalDateTime> rruleProperty = new RRule<>(rruleStr);
            vEvent.add(rruleProperty);

            // Define the calculation window range
            Period<LocalDateTime> searchPeriod = new Period<>(rangeStart, rangeEnd);

            // Calculate the set of all repeating cycles that occur within this window
            Collection<Period<LocalDateTime>> periods = vEvent.calculateRecurrenceSet(searchPeriod);

            // Get the duration of a single event
            Duration duration = Duration.between(startFirst.atZone(ZoneId.systemDefault()), endFirst.atZone(ZoneId.systemDefault()));

            // Extract LocalDateTime
            for (Period<LocalDateTime> periodInstance : periods) {
                LocalDateTime instanceStart = periodInstance.getStart();
                LocalDateTime instanceEnd = instanceStart.plus(duration);

                occurrences.add(new Occurrence(instanceStart, instanceEnd));
            }
        } catch (Exception e) {
            logger.error("Parse ical4j 4.x RRULE error, rrule: {}", rruleStr, e);
        }

        return occurrences;
    }

    /**
     * Inner class: Represents a specific instance of occurrence
     */
    public record Occurrence(LocalDateTime startTime, LocalDateTime endTime) {
    }

    /**
     * Extract the structured data required by the front-end editing page from the RRULE string
     *
     * @param rruleStr RRULE String (e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")
     * @return VO for the front end
     */
    public static RruleDisplayDto parseRrule(String rruleStr) {
        if (rruleStr == null || rruleStr.isBlank()) {
            return new RruleDisplayDto("NO_REPEAT", null, null, null, null);
        }

        try {
            RRule<LocalDateTime> rrule = new RRule<>(rruleStr);
            Recur<LocalDateTime> recur = rrule.getRecur();

            String repeatTypeName = recur.getFrequency().name();
            Integer interval = recur.getInterval() <= 0 ? 1 : recur.getInterval();

            List<String> daysOfWeek = new ArrayList<>();
            if (recur.getDayList() != null && !recur.getDayList().isEmpty()) {
                daysOfWeek = recur.getDayList().stream()
                        .map(weekDay -> weekDay.getDay().name())
                        .toList();
            }

            List<Integer> daysOfMonth = new ArrayList<>();
            if (recur.getMonthDayList() != null && !recur.getMonthDayList().isEmpty()) {
                daysOfMonth = new ArrayList<>(recur.getMonthDayList());
            }

            List<Integer> months = new ArrayList<>();
            if (recur.getMonthList() != null && !recur.getMonthList().isEmpty()) {
                months = new ArrayList<>(
                        recur.getMonthList().stream()
                                .map(Month::getMonthOfYear)
                                .toList()
                );
            }

            return new RruleDisplayDto(
                    repeatTypeName,
                    interval,
                    daysOfWeek,
                    daysOfMonth,
                    months
            );
        } catch (Exception e) {
            logger.error("Illegal RRULE: {}", rruleStr, e);
            return new RruleDisplayDto("NO_REPEAT", null, null, null, null);
        }
    }

    public record RruleDisplayDto(
            // SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
            String repeatTypeName,
            // Interval (every few days/weeks/months/years)
            Integer interval,
            // Repeating days of the week (e.g., ["MO", "WE"]), applicable to WEEKLY
            List<String> daysOfWeek,
            // Repeating numbers (e.g., [1, 15]) applies to both MONTHLY and YEARLY
            List<Integer> daysOfMonth,
            // Repeating months (e.g., [1, 6, 12]), applicable to YEARLY
            List<Integer> months
    ) {
    }
}
