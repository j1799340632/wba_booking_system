package edu.unsw.domain.vo;

import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.*;

@Getter @Setter
public class AssessmentPlanVo {
    private boolean configured;
    private Integer version=0;
    private String timeZone;
    private LocalDate today;
    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDateTime generatedAt;
    private Long daysToDeadline;
    private String status="NOT_CONFIGURED";
    private int totalTarget;
    private int completed;
    private int confirmed;
    private int pending;
    private int inProgress;
    private int remaining;
    private int toRequest;
    private int weeksRemaining;
    private int uncountedRecords;
    private LocalDateTime recommendationEnd;
    private List<TypeOption> availableTypes=List.of();
    private List<GoalProgress> goals=List.of();
    private List<Week> weeks=List.of();
    private String guidance;
    private int followUpTotal;
    private boolean hasMoreFollowUps;
    private FollowUpCounts followUpCounts=new FollowUpCounts(0,0,0,0,0);
    private List<FollowUp> followUps=List.of();

    public record FollowUpCounts(int needsAttention,int awaitingApproval,int inProgress,int upcoming,int scheduled) { }
    /** An observation of an owned booking, never an automatic reminder or status update. */
    public record FollowUp(String bookingId,String kind,Integer bookingStatus,String type,String supervisorName,
                           LocalDateTime startTime,LocalDateTime endTime,String hospital,String department,String location) { }

    public record TypeOption(String id,String name) { }

    @Getter @Setter
    public static class GoalProgress {
        private String typeId;
        private String type;
        private int target;
        private int completed;
        private int confirmed;
        private int pending;
        private int inProgress;
        private int missed;
        private int remaining;
        private int toRequest;
        private int suggestedPerWeek;
        private boolean typeAvailable;
        private String recommendationMessage;
        private List<BookingAssistantResult.Candidate> candidates=List.of();
    }

    @Getter @Setter
    public static class Week {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<WeekGoal> goals=List.of();
    }
    public record WeekGoal(String type,int toRequest,int confirmed,int pending) { }
}
