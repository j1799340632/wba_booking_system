package edu.unsw.api.aop;

import edu.unsw.core.utils.mdc.MdcUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author 徐一杰 Yijie Xu Bubble
 * @date 2026/6/21 12:17
 * @description Scheduled task aspect
 */
@Aspect
@Component
public class ScheduledAspect implements Ordered {

    @Pointcut("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public void scheduledPointCut() {

    }

    @Before(value = "scheduledPointCut()")
    public void scheduledAround(JoinPoint joinPoint) {
        // If there's a call from a higher level, use the ID from that level; otherwise, construct a new one
        MdcUtils.setTraceIdIfAbsent();
    }

    @After("scheduledPointCut()")
    public void scheduledAfter(JoinPoint joinPoint) {
        MdcUtils.clear();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
