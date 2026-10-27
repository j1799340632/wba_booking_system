package edu.unsw.domain.vo;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class SupervisorBookingResult {
    private String bookingId;
    private int status;
    private int version;
    private boolean replayed;
    private String notificationStatus="SAVED";
    private LocalDateTime completedTime;
}
