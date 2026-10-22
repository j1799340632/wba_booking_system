package edu.unsw.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 19/7/2026 21:27
 * @description AssessmentSlotRepeatTypeEnum
 */
@Getter
@AllArgsConstructor
public enum AssessmentSlotRepeatTypeEnum {
    /**
     * No repeat
     */
    NO_REPEAT(0, "NO_REPEAT"),

    /**
     * Daily
     */
    DAILY(1, "DAILY"),

    /**
     * Weekly
     */
    WEEKLY(2, "WEEKLY"),

    /**
     * Monthly
     */
    MONTHLY(3, "MONTHLY"),

    /**
     * Yearly
     */
    YEARLY(4, "YEARLY"),
    ;

    private final Integer code;
    private final String typeName;

    /**
     * Get the enumeration based on the type code
     *
     * @param code type code
     * @return Enumeration
     */
    public static AssessmentSlotRepeatTypeEnum getEnum(Integer code) {
        return Stream.of(AssessmentSlotRepeatTypeEnum.values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }

    /**
     * Get the enumeration based on the type name
     *
     * @param typeName type name
     * @return Enumeration
     */
    public static AssessmentSlotRepeatTypeEnum getEnum(String typeName) {
        return Stream.of(AssessmentSlotRepeatTypeEnum.values()).filter(e -> e.getTypeName().equals(typeName)).findFirst().orElse(null);
    }
}
