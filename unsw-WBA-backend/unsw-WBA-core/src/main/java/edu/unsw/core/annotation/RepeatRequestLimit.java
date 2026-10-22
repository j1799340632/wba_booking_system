package edu.unsw.core.annotation;

import edu.unsw.core.enums.RequestLimitTypeEnum;

import java.lang.annotation.*;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/7/14 22:23
 * @description Limiting the frequency of repeated requests can prevent users from clicking incessantly and can also be used to ensure the idempotency of an API
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatRequestLimit {
    /**
     * Request time interval, in seconds
     */
    int time() default 1;

    /**
     * The number of requests allowed within the interval, for example, if time is 5 and count is 2, it means that 2 requests are allowed within 5 seconds
     */
    int count() default 1;

    /**
     * Rate limiting type
     */
    RequestLimitTypeEnum limitType() default RequestLimitTypeEnum.DEFAULT;
}
