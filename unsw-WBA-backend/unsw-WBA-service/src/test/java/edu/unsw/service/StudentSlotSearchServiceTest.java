package edu.unsw.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.service.utils.RruleUtils;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.DataBinder;

import java.sql.SQLException;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real mapper SQL against an isolated in-memory database; no existing Docker data is used. */
class StudentSlotSearchServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 10, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T23:00:00Z"), ZoneId.of("Australia/Sydney"));
    private SqlSession session;
    private AssessmentSlotMapper slots;
    private AssessmentBookingMapper bookings;
    private UserMapper users;
    private StudentSlotSearchService service;

    @BeforeEach
    void openDatabase() throws SQLException {
        var dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER", "sa", "");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        config.addMapper(AssessmentSlotMapper.class);
        config.addMapper(AssessmentBookingMapper.class);
        config.addMapper(UserMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        for (Class<?> type : List.of(AssessmentSlot.class, AssessmentBooking.class, User.class)) {
            createTable(type);
        }
        slots = session.getMapper(AssessmentSlotMapper.class);
        bookings = session.getMapper(AssessmentBookingMapper.class);
        users = session.getMapper(UserMapper.class);
        service = new StudentSlotSearchService(slots, bookings, users, CLOCK);
        addUser(10L, "Dr Wang");
        addUser(20L, "Dr Smith");
    }

    @AfterEach
    void closeDatabase() {
        if (session != null) session.close();
    }

    @Test
    void combinesSupervisorIdNameAndDictionaryFiltersInActualSql() {
        addSlot(slot(1L, 10L, NOW.plusHours(3)));
        addSlot(slot(2L, 20L, NOW.plusHours(3)));
        AssessmentSlot wrongHospital = slot(3L, 10L, NOW.plusHours(3));
        wrongHospital.setHospital("Other Hospital");
        addSlot(wrongHospital);
        AssessmentSlot wrongType = slot(4L, 10L, NOW.plusHours(3));
        wrongType.setType("DOPS");
        addSlot(wrongType);
        AssessmentSlot wrongDepartment = slot(5L, 10L, NOW.plusHours(3));
        wrongDepartment.setDepartment("Surgery");
        addSlot(wrongDepartment);
        var query = query(NOW, NOW.plusDays(1));
        query.setSupervisorId(10L);
        query.setSupervisorName("Wang");
        query.setHospital("Demo Hospital");
        query.setDepartment("Medicine");
        query.setType("Mini-CEX");
        var result = service.search(query);
        assertEquals(1, result.getTotal());
        assertEquals(1L, result.getRecords().getFirst().getId());
        assertEquals("Dr Wang", result.getRecords().getFirst().getCreatorName());
        assertEquals("Australia/Sydney", result.getRecords().getFirst().getTimeZone());
        query.setSupervisorName("Smith");
        assertEquals(0, service.search(query).getTotal());
        query.setSupervisorName("Unknown");
        assertEquals(0, service.search(query).getTotal());
    }

    @Test
    void preservesAllSupervisorsWithTheSameMatchingName() {
        addUser(30L, "Dr Wang");
        addSlot(slot(1L, 10L, NOW.plusHours(3)));
        addSlot(slot(2L, 30L, NOW.plusHours(4)));
        var query = query(NOW, NOW.plusDays(1));
        query.setSupervisorName(" Wang ");
        assertEquals(2, service.search(query).getTotal());
    }

    @Test
    void excludesDisabledDeletedAndNonAvailableSlots() {
        addSlot(slot(1L, 10L, NOW.plusHours(3)));
        var disabled = slot(2L, 10L, NOW.plusHours(3));
        disabled.setIsEnable(false);
        addSlot(disabled);
        var deleted = slot(3L, 10L, NOW.plusHours(3));
        deleted.setIsDelete(true);
        addSlot(deleted);
        var pending = slot(4L, 10L, NOW.plusHours(3));
        pending.setStatus(1);
        addSlot(pending);
        assertEquals(List.of(1L), service.search(query(NOW, NOW.plusDays(1))).getRecords().stream().map(v -> v.getId()).toList());
    }

    @Test
    void returnsFutureRepeatsEvenWhenTheTemplateDateHasPassed() {
        var start = LocalDateTime.of(2026, 10, 27, 13, 0);
        addSlot(repeated(1L, LocalDateTime.of(2026, 10, 1, 14, 0), "FREQ=DAILY;INTERVAL=1"));
        var view = service.search(query(start, start.plusHours(4))).getRecords().getFirst();
        assertEquals(List.of(List.of(start.plusHours(1), start.plusMinutes(90))), view.getAvailableTimeList());
        assertEquals(start.plusHours(1), view.getNextAvailableTime());
        assertEquals(1, view.getRepeatType());
    }

    @Test
    void removesPendingAndConfirmedOccurrencesButKeepsDeclinedAndDeletedOnes() {
        var first = LocalDateTime.of(2026, 10, 27, 14, 0);
        addSlot(repeated(1L, first.minusDays(20), "FREQ=DAILY;INTERVAL=1"));
        addBooking(1L, 1L, first, 0, false);
        addBooking(2L, 1L, first.plusDays(1), 1, false);
        addBooking(3L, 1L, first.plusDays(2), 3, false);
        addBooking(4L, 1L, first.plusDays(3), 0, true);
        var times = service.search(query(first, first.plusDays(4))).getRecords().getFirst().getAvailableTimeList();
        assertEquals(List.of(first.plusDays(2), first.plusDays(3)), times.stream().map(t -> t.getFirst()).toList());
    }

    @Test
    void filtersAllCandidatesBeforeCountingAndPaging() {
        var start = LocalDateTime.of(2026, 10, 27, 13, 0);
        addSlot(repeated(1L, LocalDateTime.of(2026, 10, 5, 14, 0), "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO"));
        addSlot(repeated(2L, LocalDateTime.of(2026, 10, 1, 14, 0), "FREQ=DAILY;INTERVAL=1"));
        addBooking(1L, 2L, start.plusHours(1), 0, false);
        addSlot(slot(3L, 10L, start.plusHours(2)));
        addSlot(slot(4L, 10L, start.plusHours(3)));
        var query = query(start, start.plusHours(4));
        query.setPageQuantity(1);
        var firstPage = service.search(query);
        assertEquals(2, firstPage.getTotal());
        assertEquals(2, firstPage.getPages());
        assertEquals(3L, firstPage.getRecords().getFirst().getId());
        query.setPageNum(2);
        assertEquals(4L, service.search(query).getRecords().getFirst().getId());
        query.setPageNum(3);
        var beyond = service.search(query);
        assertEquals(2, beyond.getTotal());
        assertTrue(beyond.getRecords().isEmpty());
    }

    @Test
    void sortsByActualMatchingOccurrenceAndUsesStableIdTieBreaks() {
        var start = LocalDateTime.of(2026, 10, 27, 13, 0);
        var repeated = repeated(1L, start.minusDays(20).plusHours(3), "FREQ=DAILY;INTERVAL=1");
        repeated.setNextAvailableTime(start.minusDays(5));
        addSlot(repeated);
        addSlot(slot(3L, 10L, start.plusHours(1)));
        addSlot(slot(2L, 10L, start.plusHours(1)));
        assertEquals(List.of(2L, 3L, 1L), service.search(query(start, start.plusHours(4))).getRecords().stream().map(v -> v.getId()).toList());
    }

    @Test
    void requiresTheEntireSessionInsideTheWindow() {
        var start = NOW.plusHours(3);
        addSlot(slot(1L, 10L, start));
        addSlot(slot(2L, 10L, start.minusMinutes(1)));
        addSlot(slot(3L, 10L, start.plusMinutes(1)));
        assertEquals(List.of(1L), service.search(query(start, start.plusMinutes(30))).getRecords().stream().map(v -> v.getId()).toList());
    }

    @Test
    void usesSydneyClockAndDefaultsToNextThirtyDays() {
        addSlot(slot(1L, 10L, NOW.minusMinutes(30)));
        addSlot(slot(2L, 10L, NOW.plusHours(2)));
        addSlot(slot(3L, 10L, NOW.plusDays(29)));
        addSlot(slot(4L, 10L, NOW.plusDays(31)));
        var query = new AssessmentSlotQueryDto();
        query.setIsOrderByAvailableTime(true);
        assertEquals(List.of(2L, 3L), service.search(query).getRecords().stream().map(v -> v.getId()).toList());
        query.setStartTime(NOW.minusDays(1));
        query.setEndTime(NOW.minusHours(1));
        assertTrue(service.search(query).getRecords().isEmpty());
    }

    @Test
    void rejectsInvalidRangesAndPagingBeforeSearching() {
        var query = query(NOW, NOW);
        assertThrows(BusinessException.class, () -> service.search(query));
        query.setEndTime(NOW.plusDays(91));
        assertThrows(BusinessException.class, () -> service.search(query));
        query.setEndTime(NOW.plusDays(1));
        query.setPageNum(0);
        assertThrows(BusinessException.class, () -> service.search(query));
        query.setPageNum(1);
        query.setPageQuantity(0);
        assertThrows(BusinessException.class, () -> service.search(query));
    }

    @Test
    void supportsExistingUnpagedModeAndKeepsLargeIdsIntact() {
        long id = 2069245030206742539L;
        addSlot(slot(id, 10L, NOW.plusHours(3)));
        var query = query(NOW, NOW.plusDays(1));
        query.setPageQuantity(-1);
        assertEquals(id, service.search(query).getRecords().getFirst().getId());
    }

    @Test
    void bindsBothIsoAndExistingSpaceSeparatedRequestTimes() {
        for (String input : List.of("2026-10-27T13:00", "2026-10-27T13:00:00", "2026-10-27 13:00:00")) {
            var query = new AssessmentSlotQueryDto();
            var binder = new DataBinder(query);
            binder.setConversionService(new DefaultFormattingConversionService());
            binder.bind(new MutablePropertyValues(Map.of("startTime", input)));
            assertFalse(binder.getBindingResult().hasErrors(), input);
            assertEquals(LocalDateTime.of(2026, 10, 27, 13, 0), query.getStartTime());
        }
    }

    @Test
    void recurrenceKeepsWallClockDurationAcrossDstAndHostTimeZones() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String host : List.of("Australia/Sydney", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(host));
                var times = RruleUtils.calculateOccurrences("FREQ=WEEKLY;INTERVAL=1;BYDAY=SU",
                        LocalDateTime.of(2026, 10, 4, 1, 0), LocalDateTime.of(2026, 10, 4, 4, 0),
                        LocalDateTime.of(2026, 10, 11, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0));
                assertEquals(1, times.size());
                assertEquals(LocalDateTime.of(2026, 10, 11, 4, 0), times.getFirst().endTime(), host);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private AssessmentSlotQueryDto query(LocalDateTime start, LocalDateTime end) {
        var query = new AssessmentSlotQueryDto();
        query.setStartTime(start);
        query.setEndTime(end);
        query.setIsOrderByAvailableTime(true);
        return query;
    }

    private AssessmentSlot slot(long id, long creator, LocalDateTime start) {
        var slot = new AssessmentSlot();
        slot.setId(id);
        slot.setCreator(creator);
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(30));
        slot.setNextAvailableTime(start);
        slot.setRepeatType(0);
        slot.setHospital("Demo Hospital");
        slot.setDepartment("Medicine");
        slot.setType("Mini-CEX");
        slot.setIsEnable(true);
        slot.setIsDelete(false);
        slot.setStatus(0);
        slot.setVersion(0);
        return slot;
    }

    private AssessmentSlot repeated(long id, LocalDateTime template, String rule) {
        var slot = slot(id, 10L, template);
        slot.setRepeatType(rule.contains("WEEKLY") ? 2 : 1);
        slot.setRrule(rule);
        slot.setRepeatStartTime(template);
        slot.setRepeatEndTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        return slot;
    }

    private void addSlot(AssessmentSlot slot) { slots.insert(slot); }

    private void addUser(long id, String name) {
        var user = new User();
        user.setId(id);
        user.setName(name);
        user.setIsDelete(false);
        users.insert(user);
    }

    private void addBooking(long id, long slotId, LocalDateTime start, int status, boolean deleted) {
        var booking = new AssessmentBooking();
        booking.setId(id);
        booking.setAssessmentSlotId(slotId);
        booking.setStartTime(start);
        booking.setEndTime(start.plusMinutes(30));
        booking.setStatus(status);
        booking.setIsDelete(deleted);
        bookings.insert(booking);
    }

    private void createTable(Class<?> type) throws SQLException {
        TableInfo table = TableInfoHelper.getTableInfo(type);
        List<String> columns = new ArrayList<>();
        columns.add(table.getKeyColumn() + " BIGINT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> valueType = field.getPropertyType();
            String sqlType = valueType == LocalDateTime.class ? "TIMESTAMP"
                    : valueType == LocalTime.class ? "TIME"
                    : valueType == Long.class ? "BIGINT"
                    : valueType == Integer.class ? "INTEGER"
                    : valueType == Boolean.class ? "BOOLEAN"
                    : valueType == Double.class ? "DOUBLE" : "VARCHAR(4096)";
            columns.add(field.getColumn() + " " + sqlType);
        }
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE " + table.getTableName() + " (" + String.join(",", columns) + ")");
        }
    }
}
