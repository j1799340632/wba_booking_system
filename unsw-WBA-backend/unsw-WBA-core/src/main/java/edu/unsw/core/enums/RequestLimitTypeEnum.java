package edu.unsw.core.enums;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 14/7/2026 22:47
 * @description Rate limiting type
 */
public enum RequestLimitTypeEnum {
    /**
     * Default rate limiting policy, based on session id
     */
    DEFAULT,

    /**
     * Based on IP rate limiting
     */
    IP,

    /**
     * In a cluster setup, rate limiting can be applied to a specific instance
     */
    CLUSTER,
    ;
}
