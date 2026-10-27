package edu.unsw.domain.vo;

import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.*;

@Getter @Setter
public class AssistantInsightsVo {
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;
    private LocalDateTime firstRecordedAt;
    private String timeZone;
    private long observedOperations;
    private long searchRequests;
    private long searchWithAlternatives;
    private long newBookingRequests;
    private long bookingConfirmAttempts;
    private long bookingConfirmReplays;
    private long bookingConfirmFailures;
    private Long searchP50Ms;
    private Long searchP95Ms;
    private long modelCalls;
    private long callsWithKnownUsage;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long droppedEventsSinceRestart;
    private List<StageCount> stages=List.of();
    private List<Daily> days=List.of();
    private List<ModelUsage> models=List.of();

    @Getter @Setter
    public static class StageCount {
        private String stage;
        private String outcome;
        private String action;
        private long attempts;
        private long withAlternatives;
        private long modelCalls;
        private long knownUsage;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }
    @Getter @Setter
    public static class Daily {
        private LocalDate date;
        private long searches;
        private long ready;
        private long clarify;
        private long noResults;
        private long unavailable;
        private long newBookingRequests;
    }
    @Getter @Setter
    public static class ModelUsage {
        private String modelName;
        private long calls;
        private long knownUsage;
        private long totalTokens;
    }
}
