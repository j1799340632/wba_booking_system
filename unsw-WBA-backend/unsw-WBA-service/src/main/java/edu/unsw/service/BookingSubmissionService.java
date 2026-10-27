package edu.unsw.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.BookingAssistantResult.Candidate;
import edu.unsw.domain.vo.BookingVo;
import edu.unsw.service.utils.RruleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** All student booking entry points use the same database transaction and validation. */
@Service
public class BookingSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(BookingSubmissionService.class);
    private final AssessmentBookingMapper bookings;
    private final AssessmentSlotMapper slots;
    private final UserMapper users;
    private final MessageMapper messages;
    private final RedissonUtils redis;
    private final Clock clock;

    public BookingSubmissionService(AssessmentBookingMapper bookings, AssessmentSlotMapper slots, UserMapper users,
                                    MessageMapper messages, RedissonUtils redis, @Qualifier("bookingClock") Clock clock) {
        this.bookings = bookings; this.slots = slots; this.users = users;
        this.messages = messages; this.redis = redis; this.clock = clock;
    }

    public record Review(Candidate candidate, String fingerprint) { }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
    public Review review(AssessmentBookingSaveDto request) {
        return reviewReplacement(request, null);
    }

    // Validation failures are expected while screening candidate lists. No writes occur in this method,
    // so a rejected candidate must not mark a surrounding planning read transaction rollback-only.
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
    public Review reviewReplacement(AssessmentBookingSaveDto request, Long excludedBookingId) {
        basic(request);
        Long studentId = studentId();
        active(users.selectById(studentId), 4L);
        var slot = slots.selectById(request.getAssessmentSlotId());
        available(slot, request);
        var supervisor = users.selectById(slot.getCreator());
        active(supervisor, 3L);
        if (excludedBookingId == null) conflicts(studentId, slot, request);
        else if (bookings.countReplacementConflicts(studentId, slot.getCreator(), slot.getId(), request.getStartTime(),
                request.getEndTime(), excludedBookingId) > 0) fail("The replacement overlaps another booking. Choose another session.");
        var candidate = new Candidate();
        candidate.setSlotId(slot.getId().toString()); candidate.setSlotVersion(slot.getVersion());
        candidate.setSupervisorId(supervisor.getId().toString()); candidate.setSupervisorName(supervisor.getName());
        candidate.setStartTime(request.getStartTime()); candidate.setEndTime(request.getEndTime());
        candidate.setHospital(slot.getHospital()); candidate.setDepartment(slot.getDepartment());
        candidate.setLocation(slot.getLocation()); candidate.setType(slot.getType());
        return new Review(candidate, fingerprint(slot, supervisor, request));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BookingVo submit(AssessmentBookingSaveDto request, String reviewedFingerprint) {
        basic(request);
        Long studentId = studentId();
        // Participant row locks last until COMMIT, including inserts and durable notification writes.
        // Student first, then supervisor: their distinct role identities prevent lock-order cycles.
        active(users.lockBookingParticipant(studentId), 4L);
        String key = request.getRequestKey() == null ? UUID.randomUUID().toString() : request.getRequestKey().toLowerCase(Locale.ROOT);
        String requestHash = hash(request.getAssessmentSlotId(), request.getStartTime(), request.getEndTime(), request.getSlotVersion());
        var existing = bookings.findSubmission(studentId, key);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) fail("This request key was already used for different session details.");
            return replay(existing);
        }
        var initial = slots.selectById(request.getAssessmentSlotId());
        if (initial == null) fail("This session no longer exists. Search again.");
        var supervisor = users.lockBookingParticipant(initial.getCreator());
        active(supervisor, 3L);
        var slot = slots.lockForBooking(initial.getId());
        available(slot, request);
        if (!Objects.equals(slot.getCreator(), supervisor.getId())) fail("The supervisor changed. Review the session again.");
        if (reviewedFingerprint != null && !reviewedFingerprint.equals(fingerprint(slot, supervisor, request))) {
            fail("Session details changed after review. Select and review the session again.");
        }
        conflicts(studentId, slot, request);
        var booking = new AssessmentBooking();
        booking.setCreator(studentId); booking.setAssessmentSlotId(slot.getId());
        booking.setStartTime(request.getStartTime()); booking.setEndTime(request.getEndTime());
        booking.setStatus(0); booking.setCreateTime(LocalDateTime.now(clock)); booking.setIsDelete(false); booking.setVersion(0);
        booking.setRequestKey(key); booking.setRequestHash(requestHash);
        if (bookings.insert(booking) != 1) throw new BusinessException("Could not save the booking request.");
        if (Objects.equals(slot.getRepeatType(), 0)) {
            slots.update(Wrappers.<AssessmentSlot>lambdaUpdate().eq(AssessmentSlot::getId, slot.getId())
                    .set(AssessmentSlot::getStatus, 1));
        }
        var notices = persistNotices(booking, slot, supervisor);
        var result = view(booking, slot, supervisor);
        result.setNotificationStatus("SAVED");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    for (var notice : notices) redis.publish(RedisKeyConstant.SSE_NOTIFY_CHANNEL, notice);
                } catch (RuntimeException error) {
                    // Messages already committed. A live notification failure must not turn success into failure.
                    result.setNotificationStatus("LIVE_DELAYED");
                    log.warn("Booking {} saved; live notification unavailable", booking.getId());
                }
            }
        });
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BookingVo replayKey(String key) {
        var booking = bookings.findSubmission(studentId(), key);
        return booking == null ? null : replay(booking);
    }

    private BookingVo replay(AssessmentBooking booking) {
        var slot = slots.selectIncludingDeleted(booking.getAssessmentSlotId());
        if (slot == null) fail("This request was saved, but its session was removed. Check My Bookings.");
        var supervisor = users.selectById(slot.getCreator());
        var result = view(booking, slot, supervisor);
        result.setReplayed(true); result.setNotificationStatus("SAVED");
        if (Boolean.TRUE.equals(booking.getIsDelete())) result.setStatus(4);
        return result;
    }

    private void basic(AssessmentBookingSaveDto request) {
        if (request == null || request.getAssessmentSlotId() == null || request.getStartTime() == null
                || request.getEndTime() == null || request.getSlotVersion() == null || request.getSlotVersion() < 0) {
            throw new BusinessException(ResponseEnum.ILLEGAL_PARAM);
        }
        if (request.getRequestKey() != null && !request.getRequestKey().matches("[A-Za-z0-9_-]{16,64}")) {
            throw new BusinessException(ResponseEnum.ILLEGAL_PARAM);
        }
    }

    private void available(AssessmentSlot slot, AssessmentBookingSaveDto request) {
        if (slot == null || Boolean.TRUE.equals(slot.getIsDelete()) || !Boolean.TRUE.equals(slot.getIsEnable())
                || !Objects.equals(slot.getStatus(), 0)) fail("This session is no longer available. Search again.");
        if (!Objects.equals(request.getSlotVersion(), slot.getVersion())) fail("Session details changed. Review the session again.");
        var start = request.getStartTime(); var end = request.getEndTime();
        if (!start.isBefore(end) || !start.isAfter(LocalDateTime.now(clock))) fail("Choose a future session with a valid start and end time.");
        if (clock.getZone().getRules().getValidOffsets(start).size() != 1 || clock.getZone().getRules().getValidOffsets(end).size() != 1) {
            fail("This local time is ambiguous or unavailable due to daylight saving. Choose another session.");
        }
        boolean real;
        if (Objects.equals(slot.getRepeatType(), 0)) {
            real = start.equals(slot.getStartTime()) && end.equals(slot.getEndTime());
        } else {
            real = slot.getRepeatStartTime() != null && slot.getRepeatEndTime() != null
                    && !start.isBefore(slot.getRepeatStartTime()) && !end.isAfter(slot.getRepeatEndTime())
                    && slot.getRrule() != null && !slot.getRrule().isBlank()
                    && RruleUtils.calculateOccurrences(slot.getRrule(), slot.getStartTime(), slot.getEndTime(), start.minusSeconds(1), end.plusSeconds(1))
                    .stream().anyMatch(time -> start.equals(time.startTime()) && end.equals(time.endTime()));
        }
        if (!real) fail("The selected time is not an actual session. Search again.");
    }

    private void conflicts(Long studentId, AssessmentSlot slot, AssessmentBookingSaveDto request) {
        if (bookings.countBookingConflicts(studentId, slot.getCreator(), slot.getId(), request.getStartTime(), request.getEndTime()) > 0) {
            fail("The student or supervisor already has an overlapping booking, or this session was taken. Choose another session.");
        }
    }

    private void active(User user, Long role) {
        if (user == null || !role.equals(user.getRoleId()) || !Boolean.TRUE.equals(user.getIsEnable()) || Boolean.TRUE.equals(user.getIsCanceled())) {
            fail("The student or supervisor account is unavailable. Refresh or choose another supervisor.");
        }
    }

    private List<NotificationDto> persistNotices(AssessmentBooking booking, AssessmentSlot slot, User supervisor) {
        String details = slot.getType() + " on " + booking.getStartTime() + " (" + clock.getZone() + "). Reference: " + booking.getId();
        var result = new ArrayList<NotificationDto>();
        for (Long receiver : List.of(booking.getCreator(), supervisor.getId())) {
            var message = new Message();
            message.setCreator(booking.getCreator()); message.setReceiver(receiver);
            message.setContent(receiver.equals(booking.getCreator())
                    ? "Your booking request for " + details + " is saved and awaiting supervisor approval."
                    : "New booking request for " + details + ". Please review it in your upcoming sessions.");
            message.setIsDelete(false); message.setIsRead(false); message.setIsDelivery(false);
            message.setCreateTime(LocalDateTime.now(clock));
            if (messages.insert(message) != 1) throw new BusinessException("Could not save booking notifications. Nothing was booked; retry the same request.");
            var notice = new NotificationDto();
            notice.setMessageId(message.getId()); notice.setSenderId(message.getCreator()); notice.setReceiverId(receiver);
            notice.setEventName("Notification"); notice.setEventContent(message.getContent()); notice.setIsSendEmail(false);
            result.add(notice);
        }
        return result;
    }

    public BookingVo view(AssessmentBooking booking, AssessmentSlot slot, User supervisor) {
        var result = new BookingVo();
        result.setBookingId(booking.getId()); result.setSlotId(slot.getId()); result.setSupervisorId(slot.getCreator());
        result.setSupervisorName(supervisor == null ? "Supervisor" : supervisor.getName());
        result.setWbaType(slot.getType()); result.setHospital(slot.getHospital());
        result.setDepartment(slot.getDepartment()); result.setLocation(slot.getLocation());
        result.setStartTime(booking.getStartTime()); result.setEndTime(booking.getEndTime());
        result.setDurationMinutes(Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes() + "min");
        result.setStatus(booking.getStatus()); result.setVersion(booking.getVersion());
        return result;
    }

    private String fingerprint(AssessmentSlot slot, User supervisor, AssessmentBookingSaveDto request) {
        return hash(slot.getId(), slot.getVersion(), slot.getCreator(), supervisor.getName(), slot.getHospital(),
                slot.getDepartment(), slot.getLocation(), slot.getType(), slot.getStartTime(), slot.getEndTime(),
                slot.getRepeatType(), slot.getRrule(), slot.getRepeatStartTime(), slot.getRepeatEndTime(), request.getStartTime(), request.getEndTime());
    }

    public static String hash(Object... parts) {
        try {
            // Length-prefixed parts avoid collisions caused by separator characters inside display values.
            StringBuilder text = new StringBuilder();
            for (Object part : parts) { String value = String.valueOf(part); text.append(value.length()).append(':').append(value); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public static Long studentId() {
        var user = CurrentUserUtils.getUser();
        if (user == null || user.getId() == null || !"Stu".equals(user.getRoleKey())) throw new BusinessException(ResponseEnum.NO_PERMISSION);
        return user.getId();
    }

    private static void fail(String message) { throw new BusinessException(ResponseEnum.SLOT_CONFLICT.getCode(), message); }
}
