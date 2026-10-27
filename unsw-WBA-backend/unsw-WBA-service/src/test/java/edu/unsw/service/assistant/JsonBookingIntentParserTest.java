package edu.unsw.service.assistant;

import com.sun.net.httpserver.HttpServer;
import edu.unsw.core.config.BookingAiProperties;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JsonBookingIntentParserTest {
    private final JsonMapper json = new JsonMapper();
    private HttpServer server;
    private BookingAiProperties properties;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<JsonNode> received = new AtomicReference<>();
    private volatile int status;
    private volatile int delay;
    private volatile String authorization;

    @BeforeEach
    void startLocalModelFixture() throws Exception {
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            received.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // The timeout case intentionally closes the client before the fixture sends its response.
            } finally {
                exchange.close();
            }
        });
        server.start();
        properties = new BookingAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("unit-test-key");
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
        response.set(completion("{\"action\":\"SEARCH\"}", "stop"));
    }

    @AfterEach
    void stopFixture() { if (server != null) server.stop(0); }

    @Test
    void sendsJsonModeAndExplicitClockWithoutProfilesOrCredentialsInPrompt() {
        response.set(completion("""
                {"action":"SEARCH","type":"Mini-CEX","startTime":"2026-10-27T00:00:00",
                 "endTime":"2026-10-28T00:00:00","earliestTime":"12:00","latestTime":"18:00"}
                """, "stop"));
        var intent = new JsonBookingIntentParser(properties).parse(context());
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), intent.startTime());
        assertEquals("json_object", received.get().path("response_format").path("type").asString());
        assertEquals("disabled", received.get().path("thinking").path("type").asString());
        assertFalse(received.get().path("stream").asBoolean());
        assertEquals("Bearer unit-test-key", authorization);
        JsonNode input = json.readTree(received.get().path("messages").path(1).path("content").asString());
        assertEquals("2026-10-22T10:00", input.path("now").asString());
        assertEquals("2026-10-26T00:00", input.path("nextWeekStart").asString());
        assertEquals("2026-10-27",input.path("nextWeekDates").path("TUESDAY").asString());
        assertEquals("2026-10-24",input.path("thisWeekDates").path("SATURDAY").asString());
        assertEquals("Australia/Sydney", input.path("timeZone").asString());
        assertFalse(input.toString().contains("unit-test-key"));
        assertFalse(input.has("userId"));
        assertEquals("之前的查询", input.path("history").path(0).asString());
    }

    @Test
    void disabledOrMissingKeyDoesNotMakeANetworkRequest() {
        properties.setApiKey("");
        var parser = new JsonBookingIntentParser(properties);
        assertFalse(parser.available());
        assertThrows(IllegalStateException.class, () -> parser.parse(context()));
        assertNull(received.get());
        properties.setApiKey("unit-test-key");
        properties.setEnabled(false);
        assertFalse(parser.available());
    }

    @Test
    void providerErrorsDoNotSurfaceTheirBody() {
        status = 429;
        response.set("private-provider-details");
        var exception = assertThrows(IllegalStateException.class, () -> new JsonBookingIntentParser(properties).parse(context()));
        assertFalse(exception.toString().contains("private-provider-details"));
        assertNull(exception.getCause());
    }

    @Test
    void rejectsTruncatedEmptyMarkdownAndMalformedResponses() {
        var parser = new JsonBookingIntentParser(properties);
        response.set(completion("{\"action\":\"SEARCH\"}", "length"));
        assertThrows(IllegalStateException.class, () -> parser.parse(context()));
        for (String content : List.of("", "```json\n{\"action\":\"SEARCH\"}\n```", "{not-json}")) {
            response.set(completion(content, "stop"));
            assertThrows(IllegalStateException.class, () -> parser.parse(context()));
        }
    }

    @Test
    void rejectsExecutableActionsInventedIdsAndCandidates() {
        var parser = new JsonBookingIntentParser(properties);
        for (String content : List.of("{\"action\":\"BOOK\"}",
                "{\"action\":\"SEARCH\",\"supervisorId\":\"10\"}",
                "{\"action\":\"SEARCH\",\"candidates\":[{\"slotId\":\"invented\"}]}")) {
            response.set(completion(content, "stop"));
            assertThrows(IllegalStateException.class, () -> parser.parse(context()));
        }
    }

    @Test
    void rejectsWrongTypesInvalidWeekdaysAndContradictoryPreferences() {
        var parser = new JsonBookingIntentParser(properties);
        for (String content : List.of("{\"action\":\"SEARCH\",\"weekdays\":[\"2\"]}",
                "{\"action\":\"SEARCH\",\"weekdays\":[8]}",
                "{\"action\":\"SEARCH\",\"startTime\":\"not-a-date\"}",
                "{\"action\":\"SEARCH\",\"supervisorName\":\"Wang\",\"supervisorMode\":\"ANY\"}")) {
            response.set(completion(content, "stop"));
            assertThrows(IllegalStateException.class, () -> parser.parse(context()));
        }
    }

    @Test
    void modelTimeoutIsBoundedAndDoesNotReturnPartialResults() {
        properties.setTimeoutSeconds(1);
        delay = 1500;
        assertThrows(IllegalStateException.class, () -> new JsonBookingIntentParser(properties).parse(context()));
    }

    @Test void observationsKeepUsageFromRejectedModelContentAndDistinguishMissingUsage() {
        var parser=new JsonBookingIntentParser(properties);
        var body=json.createObjectNode();body.set("choices",json.readTree(completion("{not-json}","stop")).path("choices"));
        body.set("usage",json.readTree("{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120}"));response.set(json.writeValueAsString(body));
        try(var observation=BookingModelObservation.begin()) {
            assertThrows(IllegalStateException.class,()->parser.parse(context()));assertEquals(1,observation.calls());assertTrue(observation.usageKnown());assertEquals(120,observation.totalTokens());
        }
        response.set(completion("{\"action\":\"SEARCH\"}","stop"));
        try(var observation=BookingModelObservation.begin()) {parser.parse(context());assertEquals(1,observation.calls());assertFalse(observation.usageKnown());}
    }

    @Test void invalidOrPartialUsageIsNeverReportedAsKnownConsumption() {
        var parser=new JsonBookingIntentParser(properties);
        for(String usage:List.of("{\"prompt_tokens\":1}","{\"prompt_tokens\":-1,\"completion_tokens\":2,\"total_tokens\":1}","{\"prompt_tokens\":\"1\",\"completion_tokens\":2,\"total_tokens\":3}")) {
            var body=json.createObjectNode();body.set("choices",json.readTree(completion("{\"action\":\"SEARCH\"}","stop")).path("choices"));body.set("usage",json.readTree(usage));response.set(json.writeValueAsString(body));
            try(var observation=BookingModelObservation.begin()) {parser.parse(context());assertFalse(observation.usageKnown());}
        }
    }

    @Test void managementSeparatesOriginalAndReplacementAndRejectsIdsOrRecursiveTargets() {
        var parser=new JsonBookingIntentParser(properties);
        response.set(completion("""
            {"action":"RESCHEDULE_BOOKING","startTime":"2026-10-27T00:00:00","endTime":"2026-10-28T00:00:00",
             "bookingStatus":1,"replacement":{"action":"SEARCH","startTime":"2026-10-29T12:00:00","endTime":"2026-10-29T18:00:00"}}
            ""","stop"));
        var intent=parser.parse(context());
        assertEquals(BookingIntent.Action.RESCHEDULE_BOOKING,intent.action());
        assertEquals(27,intent.startTime().getDayOfMonth()); assertEquals(29,intent.replacement().startTime().getDayOfMonth());
        for(String content:List.of("{\"action\":\"CANCEL_BOOKING\",\"bookingId\":1}",
                "{\"action\":\"RESCHEDULE_BOOKING\",\"replacement\":{\"action\":\"CANCEL_BOOKING\"}}",
                "{\"action\":\"SEARCH\",\"bookingStatus\":1}",
                "{\"action\":\"LIST_BOOKINGS\",\"bookingStatus\":9}",
                "{\"action\":\"RESCHEDULE_BOOKING\",\"replacement\":{\"action\":\"SEARCH\",\"replacement\":{\"action\":\"SEARCH\"}}}")) {
            response.set(completion(content,"stop")); assertThrows(IllegalStateException.class,()->parser.parse(context()));
        }
    }

    private BookingIntentParser.Context context() {
        return new BookingIntentParser.Context("下周二下午的 Mini-CEX", List.of("之前的查询"),
                LocalDateTime.of(2026, 10, 22, 10, 0), "Australia/Sydney",
                List.of("Demo Hospital"), List.of("Medicine"), List.of("Mini-CEX"));
    }

    private String completion(String content, String finish) {
        return json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", finish,
                "message", Map.of("role", "assistant", "content", content)))));
    }
}
