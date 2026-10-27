package edu.unsw.domain.vo;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class BookingManagementVo {
    private String draftId;
    private String action;
    private LocalDateTime expiresAt;
    private String timeZone;
    private BookingVo original;
    private BookingAssistantResult.Candidate replacement;
    private BookingVo newBooking;
    private boolean replayed;
    private String notificationStatus;
}
