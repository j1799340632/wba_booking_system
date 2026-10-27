package edu.unsw.service.assistant;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.vo.*;
import edu.unsw.service.BookingSubmissionService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.UUID;

@Service
public class BookingConfirmationService {
    private final BookingSubmissionService submissions;
    private final RedissonUtils redis;
    private final Clock clock;
    private static final String PREFIX = "booking:review:";

    public BookingConfirmationService(BookingSubmissionService submissions, RedissonUtils redis,
                                       @Qualifier("bookingClock") Clock clock) {
        this.submissions = submissions; this.redis = redis; this.clock = clock;
    }

    @Getter @Setter
    public static class StoredDraft {
        private AssessmentBookingSaveDto request;
        private String fingerprint;
        private LocalDateTime expiresAt;
    }

    public BookingDraftVo prepare(AssessmentBookingSaveDto selection) {
        Long owner = studentId();
        var review = submissions.review(selection);
        String id = UUID.randomUUID().toString();
        // Store an independent server-owned request; confirmation accepts no editable booking fields.
        var request = new AssessmentBookingSaveDto();
        request.setAssessmentSlotId(selection.getAssessmentSlotId()); request.setSlotVersion(selection.getSlotVersion());
        request.setStartTime(selection.getStartTime()); request.setEndTime(selection.getEndTime()); request.setRequestKey("ai_" + id);
        var stored = new StoredDraft(); stored.setRequest(request); stored.setFingerprint(review.fingerprint());
        stored.setExpiresAt(LocalDateTime.now(clock).plusMinutes(10));
        redis.setObject(PREFIX + owner + ":" + id, stored, Duration.ofMinutes(10));
        var result = new BookingDraftVo(); result.setDraftId(id); result.setExpiresAt(stored.getExpiresAt());
        result.setTimeZone(clock.getZone().getId()); result.setSession(review.candidate());
        return result;
    }

    public BookingVo confirm(BookingConfirmationRequest confirmation) {
        Long owner = studentId();
        if (!Boolean.TRUE.equals(confirmation.getConfirmed())) throw new BusinessException(ResponseEnum.ILLEGAL_PARAM);
        String id = confirmation.getDraftId();
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new BusinessException(ResponseEnum.ILLEGAL_PARAM);
        var previous = submissions.replayKey("ai_" + id);
        if (previous != null) return previous; // Also resolves a lost response after the draft expires.
        StoredDraft draft = redis.getObject(PREFIX + owner + ":" + id);
        if (draft == null || !draft.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ResponseEnum.VERIFICATION_EXPIRED.getCode(), "This review expired. Select and review the session again.");
        }
        return submissions.submit(draft.getRequest(), draft.getFingerprint());
    }

    private static Long studentId() {
        var user = CurrentUserUtils.getUser();
        if (user == null || user.getId() == null || !"Stu".equals(user.getRoleKey())) throw new BusinessException(ResponseEnum.NO_PERMISSION);
        return user.getId();
    }
}
