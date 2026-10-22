package edu.unsw.api.aop;

import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author 徐一杰 Yijie Xu Bubble
 * @date 2026/7/16 17:27
 * @description AOP log interception currently only displays console output; however, logs for methods annotated with AOP can be saved to a database in the future if needed
 */
@Aspect
@Component
public class LogAspect {
    private final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Pointcut("execution(* edu.unsw.api.controller..*.*(..))")
    public void logPointCut() {

    }

    @Before("logPointCut()")
    public void doBefore(JoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = Objects.requireNonNull(attributes).getRequest();
        String ipAddr = IpUtils.getIpAddr(request);
        CurrentUser currentUser = CurrentUserUtils.getUser();
        logger.info(
                "Request source IP: {}, source user: {}, URI: {}, Method: {}",
                ipAddr,
                Objects.isNull(currentUser) ?  null : currentUser.getId(),
                request.getRequestURI(),
                request.getMethod()
        );

        if (logger.isDebugEnabled()) {
            String requestParam = Arrays.toString(joinPoint.getArgs());
            logger.debug(
                    "Package path: {}, Request params: {}",
                    joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName(),
                    requestParam
            );
        }
    }

    @AfterReturning(returning = "ret", pointcut = "logPointCut()")
    public void doAfterReturning(Object ret) {
        if (logger.isDebugEnabled()) {
            logger.debug("Return value: {}", ret);
        }
    }

    @Around("logPointCut()")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        // ob is the return value of the method
        Object ob = pjp.proceed();
        long processPeriod = System.currentTimeMillis() - startTime;
        logger.info("Time cost: {}ms", processPeriod);
        return ob;
    }
}
