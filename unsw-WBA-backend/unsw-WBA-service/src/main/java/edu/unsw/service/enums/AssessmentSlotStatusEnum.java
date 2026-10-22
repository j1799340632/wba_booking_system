package edu.unsw.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 25/6/2026 02:53
 * @description AssessmentSlotStatusEnum
 */
@Getter
@AllArgsConstructor
public enum AssessmentSlotStatusEnum {
    /**
     * Available
     */
    AVAILABLE(0, "Available"),

    /**
     * Booked
     */
    PENDING(1, "Pending"),

    /**
     * Assessment completed
     */
    COMPLETED(2, "Completed"),
    ;

    private final Integer code;
    private final String status;

    /**
     * Get the enumeration based on the status code
     *
     * @param code status code
     * @return Enumeration
     */
    public static AssessmentSlotStatusEnum getEnum(Integer code) {
        return Stream.of(AssessmentSlotStatusEnum.values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }
}
