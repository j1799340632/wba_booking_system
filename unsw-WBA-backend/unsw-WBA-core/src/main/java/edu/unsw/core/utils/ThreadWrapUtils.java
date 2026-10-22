package edu.unsw.core.utils;

import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.mdc.MdcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 20:02
 * @description Thread data transfer utility class
 */
public class ThreadWrapUtils {
    private ThreadWrapUtils() {

    }

    private static final Logger logger = LoggerFactory.getLogger(ThreadWrapUtils.class);

    /**
     * Set the log tracking thread context and tranceId
     *
     * @param context thread context
     */
    private static void setContextAndTraceId(Map<String, String> context) {
        if (CollectionUtils.isEmpty(context)) {
            MdcUtils.clear();
        } else {
            MdcUtils.setContextMap(context);
        }
        MdcUtils.setTraceIdIfAbsent();
    }

    public static <T> Callable<T> wrap(final Callable<T> callable) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        Map<String, String> context = MdcUtils.getContextMap();
        Map<String, String> contextSnapshot = CollectionUtils.isEmpty(context)
                ? null
                : new HashMap<>(context);
        return () -> {
            CurrentUserUtils.setUser(currentUser);
            setContextAndTraceId(contextSnapshot);
            try {
                logger.info("Callable thread user: {}", currentUser);
                return callable.call();
            } finally {
                CurrentUserUtils.remove();
                MdcUtils.clear();
            }
        };
    }

    public static Runnable wrap(final Runnable runnable) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        Map<String, String> context = MdcUtils.getContextMap();
        Map<String, String> contextSnapshot = CollectionUtils.isEmpty(context)
                ? null
                : new HashMap<>(context);
        return () -> {
            CurrentUserUtils.setUser(currentUser);
            setContextAndTraceId(contextSnapshot);
            try {
                logger.info("Runnable thread user: {}", currentUser);
                runnable.run();
            } finally {
                CurrentUserUtils.remove();
                MdcUtils.clear();
            }
        };
    }
}
