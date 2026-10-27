import edu.unsw.core.config.BookingAiProperties;
import edu.unsw.service.assistant.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Explicitly run with scripts/evaluate_booking_ai.py --live; never runs during Maven tests. */
class BookingIntentEvaluation {
    static final JsonMapper JSON = new JsonMapper();
    public static void main(String[] args) throws Exception {
        var suite = JSON.readTree(Files.readString(Path.of(args[0])));
        Path output = Path.of(args[1]);
        var properties = new BookingAiProperties();
        properties.setEnabled(true); properties.setApiKey(env("BOOKING_AI_API_KEY", ""));
        properties.setEndpoint(env("BOOKING_AI_ENDPOINT", properties.getEndpoint()));
        properties.setModel(env("BOOKING_AI_MODEL", properties.getModel()));
        properties.setTimeoutSeconds(Integer.parseInt(env("BOOKING_AI_TIMEOUT_SECONDS", "20")));
        properties.setDisableThinking(Boolean.parseBoolean(env("BOOKING_AI_DISABLE_THINKING", "true")));
        var parser = new JsonBookingIntentParser(properties);
        if (!parser.available()) throw new IllegalStateException("Configure a backend model key before live evaluation.");
        var cases = new ArrayList<Map<String, Object>>();
        for (JsonNode test : suite.path("cases")) {
            var row = new LinkedHashMap<String, Object>(); row.put("id", test.path("id").asString());
            long began = System.nanoTime();
            var failures = new ArrayList<String>();
            try {
                var intent = parser.parse(new BookingIntentParser.Context(test.path("message").asString(),
                    strings(test.path("history")), LocalDateTime.parse(suite.path("referenceTime").asString()),
                    suite.path("timeZone").asString(), strings(suite.path("hospitals")),
                    strings(suite.path("departments")), strings(suite.path("types"))));
                var expected = test.path("expected");
                if (!strings(expected.path("actions")).contains(intent.action().name())) failures.add("action");
                if (intent.action() == BookingIntent.Action.CLARIFY && (intent.question() == null || intent.question().isBlank())) failures.add("missing_clarification_question");
                var extracted = fields(intent); row.put("extracted", extracted);
                if (Set.of(BookingIntent.Action.SEARCH,BookingIntent.Action.LIST_BOOKINGS,BookingIntent.Action.CANCEL_BOOKING,BookingIntent.Action.RESCHEDULE_BOOKING).contains(intent.action())) {
                    var actual = JSON.readTree(JSON.writeValueAsString(extracted));
                    for (String key : expected.path("fields").propertyNames()) {
                        if (!Objects.equals(expected.path("fields").get(key), actual.get(key))) failures.add("field:" + key);
                    }
                    for (String value : strings(expected.path("accepts"))) if (!matches(intent, value)) failures.add("excluded_expected_session:" + value);
                    for (String value : strings(expected.path("rejects"))) if (matches(intent, value)) failures.add("included_invalid_session:" + value);
                    if (expected.has("replacement")) {
                        if (intent.replacement()==null) failures.add("missing_replacement");
                        else {
                            var target=expected.path("replacement"); var actualTarget=JSON.readTree(JSON.writeValueAsString(fields(intent.replacement())));
                            for(String key:target.path("fields").propertyNames()) if(!Objects.equals(target.path("fields").get(key),actualTarget.get(key))) failures.add("replacement_field:"+key);
                            for(String value:strings(target.path("accepts"))) if(!matches(intent.replacement(),value)) failures.add("replacement_excluded:"+value);
                            for(String value:strings(target.path("rejects"))) if(matches(intent.replacement(),value)) failures.add("replacement_included:"+value);
                        }
                    }
                }
            } catch (RuntimeException error) {
                // Never persist provider error bodies, credentials, headers, or arbitrary stack traces.
                failures.add("adapter_unavailable_or_invalid_response");
            }
            row.put("elapsedMs", (System.nanoTime() - began) / 1_000_000);
            row.put("passed", failures.isEmpty()); row.put("failures", failures);
            var usage = parser.lastUsage();
            row.put("promptTokens", usage.promptTokens()); row.put("completionTokens", usage.completionTokens()); row.put("totalTokens", usage.totalTokens());
            cases.add(row);
            writeReport(output, cases, suite, properties.getModel());
            if (cases.size() % 5 == 0 || cases.size() == suite.path("cases").size()) {
                System.out.println(JSON.writeValueAsString(Map.of("completed", cases.size(), "passed", passed(cases))));
            }
        }
        System.out.println(JSON.writeValueAsString(Map.of("report", output.toString(), "cases", cases.size(), "passed", passed(cases))));
    }

    static void writeReport(Path output, List<Map<String, Object>> cases, JsonNode suite, String model) throws Exception {
        var durations = cases.stream().mapToLong(row -> ((Number) row.get("elapsedMs")).longValue()).sorted().toArray();
        long totalTokens = cases.stream().mapToLong(row -> ((Number) row.get("totalTokens")).longValue()).sum();
        var report = new LinkedHashMap<String, Object>();
        report.put("scope", "real provider intent extraction; synthetic dictionary and session probes; no booking writes");
        report.put("model", model); report.put("referenceTime", suite.path("referenceTime").asString());
        report.put("timeZone", suite.path("timeZone").asString());
        report.put("planned", suite.path("cases").size()); report.put("completed", cases.size()); report.put("passed", passed(cases));
        report.put("casePassRate", (double) passed(cases) / cases.size());
        report.put("medianMs", durations[(durations.length - 1) / 2]);
        report.put("p95Ms", durations[Math.max(0, (int) Math.ceil(durations.length * .95) - 1)]);
        report.put("totalTokens", totalTokens);
        report.put("tokenUsageAvailable", totalTokens > 0); report.put("bookingWrites", 0); report.put("cases", cases);
        Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
    }
    static long passed(List<Map<String, Object>> cases) { return cases.stream().filter(row -> Boolean.TRUE.equals(row.get("passed"))).count(); }
    static List<String> strings(JsonNode values) {
        var result = new ArrayList<String>(); if (values.isArray()) for (JsonNode value : values) result.add(value.asString()); return result;
    }
    static Map<String, Object> fields(BookingIntent intent) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("action", intent.action().name()); fields.put("hospital", intent.hospital()); fields.put("department", intent.department());
        fields.put("type", intent.type()); fields.put("supervisorName", intent.supervisorName()); fields.put("supervisorMode", intent.supervisorMode().name());
        fields.put("startTime", str(intent.startTime())); fields.put("endTime", str(intent.endTime()));
        fields.put("earliestTime", str(intent.earliestTime())); fields.put("latestTime", str(intent.latestTime())); fields.put("weekdays", intent.weekdays());
        fields.put("bookingStatus",intent.bookingStatus());
        if(intent.replacement()!=null) fields.put("replacement",fields(intent.replacement()));
        return fields;
    }
    static String str(Object value) { return value == null ? null : value.toString(); }
    static boolean matches(BookingIntent intent, String timestamp) {
        var start = LocalDateTime.parse(timestamp); var end = start.plusMinutes(30);
        return (intent.startTime() == null || !start.isBefore(intent.startTime()))
            && (intent.endTime() == null || !end.isAfter(intent.endTime()))
            && (intent.earliestTime() == null || !start.toLocalTime().isBefore(intent.earliestTime()))
            && (intent.latestTime() == null || !end.toLocalTime().isAfter(intent.latestTime()))
            && (intent.weekdays().isEmpty() || intent.weekdays().contains(start.getDayOfWeek().getValue()));
    }
    static String env(String key, String fallback) { var value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
}
