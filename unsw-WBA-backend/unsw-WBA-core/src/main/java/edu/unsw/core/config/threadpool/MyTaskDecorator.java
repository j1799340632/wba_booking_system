package edu.unsw.core.config.threadpool;

import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.mdc.MdcUtils;
import jakarta.annotation.Nonnull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:55
 * @description Thread data transfer
 */
public class MyTaskDecorator implements TaskDecorator {
    @Nonnull
    @Override
    public Runnable decorate(@Nonnull Runnable runnable) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        Map<String, String> context = MdcUtils.getContextMap();
        Map<String, String> contextSnapshot = CollectionUtils.isEmpty(context)
                ? null
                : new HashMap<>(context);
        return () -> {
            try{
                CurrentUserUtils.setUser(currentUser);
                if (CollectionUtils.isEmpty(context)) {
                    MdcUtils.clear();
                } else {
                    MdcUtils.setContextMap(contextSnapshot);
                }
                MdcUtils.setTraceIdIfAbsent();
                runnable.run();
            } finally {
                CurrentUserUtils.remove();
                MdcUtils.clear();
            }
        };
    }
}
