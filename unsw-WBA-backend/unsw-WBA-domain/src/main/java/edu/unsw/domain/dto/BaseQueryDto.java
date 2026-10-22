package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/6/2026 16:33
 * @description Base query field
 */
@Data
@Schema(title = "Base Query field")
public class BaseQueryDto {
    public BaseQueryDto() {
        this.pageNum = 1;
        this.pageQuantity = 10;
    }

    @Schema(description = "Page number")
    private Integer pageNum;

    @Schema(description = "Data amount per page, when pageQuantity is -1, it means no limit")
    private Integer pageQuantity;

    @Schema(description = "Start time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, fallbackPatterns = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "End time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, fallbackPatterns = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
