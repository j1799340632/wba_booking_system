package edu.unsw.domain.vo;

import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BookingAssistantResult {
    public enum Status { READY, BOOKINGS, PLAN, CLARIFY, NO_RESULTS, UNAVAILABLE, UNSUPPORTED }
    private Status status;
    private String message;
    private String traceId;
    private String timeZone;
    private long elapsedMs;
    private AssessmentSlotQueryDto conditions;
    private String preferredSupervisor;
    private List<Candidate> candidates = List.of();
    private List<Alternative> alternatives = List.of();
    private String bookingAction;
    private List<BookingVo> bookings = List.of();
    private AssessmentSlotQueryDto replacementConditions;
    private boolean hasMoreBookings;
    private AssessmentPlanVo assessmentPlan;

    @Getter @Setter
    public static class Alternative {
        private String kind;
        private String title;
        private String explanation;
        private AssessmentSlotQueryDto conditions;
        private List<Candidate> candidates=List.of();
    }

    @Getter
    @Setter
    public static class Candidate {
        private String slotId;
        private Integer slotVersion;
        private String supervisorId;
        private String supervisorName;
        private String hospital;
        private String department;
        private String type;
        private String location;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean preferredSupervisor;
    }
}
