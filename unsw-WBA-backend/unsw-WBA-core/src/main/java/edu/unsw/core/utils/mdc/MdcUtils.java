package edu.unsw.core.utils.mdc;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 20:01
 * @description MDC Log Tracing Utilities
 */
public class MdcUtils {
    private MdcUtils() {

    }

    public static final String TRACE_ID = "traceId";

    public static Map<String, String> getContextMap() {
        return MDC.getCopyOfContextMap();
    }

    public static void setContextMap(Map<String, String> context) {
        MDC.setContextMap(context);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void setTraceIdIfAbsent() {
        setTraceIdIfAbsent("");
    }

    public static void setTraceIdIfAbsent(String traceId) {
        if (!StringUtils.hasLength(getTraceId())) {
            if (StringUtils.hasLength(traceId)) {
                setTraceId(traceId);
            } else {
                setTraceId(generateTraceId());
            }
        }
    }

    public static void removeTraceId() {
        MDC.remove(TRACE_ID);
    }

    public static void clear() {
        MDC.clear();
    }
}
