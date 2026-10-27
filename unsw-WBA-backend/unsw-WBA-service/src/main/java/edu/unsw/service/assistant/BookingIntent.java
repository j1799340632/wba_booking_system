package edu.unsw.service.assistant;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** A model can describe a search, but cannot specify executable tools, identities, or bookings. */
public record BookingIntent(Action action, String hospital, String department, String type,
                            String supervisorName, SupervisorMode supervisorMode,
                            LocalDateTime startTime, LocalDateTime endTime,
                            LocalTime earliestTime, LocalTime latestTime, List<Integer> weekdays, String question,
                            Integer bookingStatus, BookingIntent replacement) {
    public BookingIntent(Action action, String hospital, String department, String type, String supervisorName,
            SupervisorMode supervisorMode, LocalDateTime startTime, LocalDateTime endTime, LocalTime earliestTime,
            LocalTime latestTime, List<Integer> weekdays, String question) {
        this(action,hospital,department,type,supervisorName,supervisorMode,startTime,endTime,earliestTime,latestTime,weekdays,question,null,null);
    }
    public enum Action { SEARCH, LIST_BOOKINGS, CANCEL_BOOKING, RESCHEDULE_BOOKING, PLAN_ASSESSMENTS, CLARIFY, UNSUPPORTED }
    public enum SupervisorMode { ANY, REQUIRED, PREFERRED }
    private static final Set<String> FIELDS = Set.of("action", "hospital", "department", "type", "supervisorName",
            "supervisorMode", "startTime", "endTime", "earliestTime", "latestTime", "weekdays", "question", "bookingStatus", "replacement");

    public static BookingIntent fromJson(JsonNode node) {
        return fromJson(node, false);
    }

    private static BookingIntent fromJson(JsonNode node, boolean nested) {
        if (!node.isObject()) throw new IllegalArgumentException("Expected search object");
        node.propertyNames().forEach(key -> {
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("Unexpected search field");
        });
        Action action = Action.valueOf(Objects.requireNonNull(text(node, "action")));
        if (nested && action != Action.SEARCH) throw new IllegalArgumentException("Replacement must be a search");
        Integer status = null;
        JsonNode statusNode = node.get("bookingStatus");
        if (statusNode != null && !statusNode.isNull()) {
            if (!statusNode.isIntegralNumber() || statusNode.asInt()<0 || statusNode.asInt()>4 || action==Action.SEARCH) throw new IllegalArgumentException("Invalid booking status");
            status=statusNode.asInt();
        }
        BookingIntent replacement=null;
        JsonNode target=node.get("replacement");
        if (target!=null && !target.isNull()) {
            if (nested || action!=Action.RESCHEDULE_BOOKING) throw new IllegalArgumentException("Unexpected replacement");
            replacement=fromJson(target,true);
        }
        String supervisor = text(node, "supervisorName");
        String mode = text(node, "supervisorMode");
        SupervisorMode preference = mode == null ? (supervisor == null ? SupervisorMode.ANY : SupervisorMode.REQUIRED)
                : SupervisorMode.valueOf(mode);
        if ((supervisor == null) != (preference == SupervisorMode.ANY)) {
            throw new IllegalArgumentException("Inconsistent supervisor preference");
        }
        List<Integer> weekdays = new ArrayList<>();
        JsonNode days = node.get("weekdays");
        if (days != null && !days.isNull()) {
            if (!days.isArray() || days.size() > 7) throw new IllegalArgumentException("Invalid weekdays");
            for (JsonNode day : days) {
                if (!day.isIntegralNumber() || day.asInt() < 1 || day.asInt() > 7) throw new IllegalArgumentException("Invalid weekday");
                weekdays.add(day.asInt());
            }
        }
        return new BookingIntent(action, text(node, "hospital"), text(node, "department"), text(node, "type"),
                supervisor, preference, date(node, "startTime"), date(node, "endTime"),
                time(node, "earliestTime"), time(node, "latestTime"), List.copyOf(weekdays), text(node, "question"), status, replacement);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString() || value.asString().length() > 250) throw new IllegalArgumentException("Invalid search value");
        String result = value.asString().trim();
        return result.isEmpty() ? null : result;
    }

    private static LocalDateTime date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private static LocalTime time(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalTime.parse(value);
    }
}
