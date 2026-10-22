package edu.unsw.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * @author WBA Connect
 * @date 14/7/2026 14:55
 * @description Registrar dashboard weekly activity
 */
@Data
@Schema(title = "Registrar dashboard weekly activity")
public class WeeklyActivityVo {
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "ISO week start date", example = "2026-07-20", type = "string", format = "date")
    private LocalDate date;

    @Schema(description = "Number of slots in this week")
    private Long slots;

    @Schema(description = "Number of bookings in this week")
    private Long bookings;

    @Schema(description = "Number of completed bookings in this week")
    private Long completed;
}
