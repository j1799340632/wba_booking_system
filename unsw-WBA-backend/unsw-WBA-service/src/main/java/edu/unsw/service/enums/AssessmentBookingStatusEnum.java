package edu.unsw.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 02:53
 * @description AssessmentBookingStatusEnum
 */
@Getter
@AllArgsConstructor
public enum AssessmentBookingStatusEnum {
    /**
     * Pending
     */
    PENDING(0, "Pending"),

    /**
     * Confirmed
     */
    CONFIRMED(1, "Confirmed"),

    /**
     * Completed
     */
    COMPLETED(2, "Completed"),

    /**
     * Declined
     */
    DECLINED(3, "Declined"),

    /**
     * Canceled
     */
    CANCELED(4, "Canceled"),
    ;

    private final Integer code;
    private final String status;

    /**
     * Get the enumeration based on the status code
     *
     * @param code status code
     * @return Enumeration
     */
    public static AssessmentBookingStatusEnum getEnum(Integer code) {
        return Stream.of(AssessmentBookingStatusEnum.values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }
}
