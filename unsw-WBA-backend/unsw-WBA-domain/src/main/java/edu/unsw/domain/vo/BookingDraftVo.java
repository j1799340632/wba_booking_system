package edu.unsw.domain.vo;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class BookingDraftVo {
    private String draftId;
    private LocalDateTime expiresAt;
    private String timeZone;
    private BookingAssistantResult.Candidate session;
    private String message = "Review this session. Confirming creates a request awaiting supervisor approval.";
}
