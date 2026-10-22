package edu.unsw.core.interceptor;

import edu.unsw.core.annotation.RepeatRequestLimit;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.RequestLimitTypeEnum;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.IpUtils;
import edu.unsw.core.utils.RedissonUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RScript;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;

/**
 * @author 徐一杰
 * @date 2026/7/14 23:26
 * @description Annotation-based request frequency limiting can prevent users from clicking excessively and can also be used to ensure API idempotency
 */
@Component
public class RepeatRequestInterceptor implements HandlerInterceptor {

    private final Logger logger = LoggerFactory.getLogger(RepeatRequestInterceptor.class);

    /**
     * Cache Lua script path
     */
    private static final String LUA_SCRIPT_PATH = "lua/rateLimit.lua";

    /**
     * Using `volatile` ensures the visibility of Lua's `Sha1` across different threads
     */
    private volatile String luaSha1;

    private final RedissonUtils redissonUtils;

    public RepeatRequestInterceptor(RedissonClient infinityRedissonClient) {
        this.redissonUtils = new RedissonUtils(infinityRedissonClient);
    }

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        if (handler instanceof HandlerMethod method) {
            // Prioritize retrieving requests annotated with @RepeatRequestLimit on methods
            RepeatRequestLimit repeatRequestLimit = AnnotationUtils.findAnnotation(method.getMethod(), RepeatRequestLimit.class);
            // The method has no annotations; retrieve the annotations from the `@RepeatRequestLimit` annotation on the controller class
            if (Objects.isNull(repeatRequestLimit)) {
                repeatRequestLimit = AnnotationUtils.findAnnotation(method.getMethod().getDeclaringClass(), RepeatRequestLimit.class);
            }

            // Annotations that restrict duplicate submissions on methods or classes
            if (Objects.nonNull(repeatRequestLimit)) {
                RequestLimitTypeEnum limitType = repeatRequestLimit.limitType();
                // Build keys based on different rate-limiting types
                String key = combinationKey(request, limitType);
                boolean isLimit = myRateLimit(key, repeatRequestLimit);
                if (isLimit) {
                    throw new BusinessException(ResponseEnum.REQUEST_EXCEED_PERIOD);
                }
            }
        }
        return true;
    }

    /**
     * I feel that Redisson's built-in rate limiting method is not as good as the one I wrote myself, because the rate limiting keys in Redisson do not have an expiration time, so they remain in Redis without being cleared
     *
     * @param key                key
     * @param repeatRequestLimit annotation
     * @return Is limit?
     */
    private boolean myRateLimit(String key, RepeatRequestLimit repeatRequestLimit) {
        // The number of limit requests
        int limitCount = repeatRequestLimit.count();
        // Limiting the time for repeated requests
        int requestPeriod = repeatRequestLimit.time();

        // The first request did not include Sha1 with cached Lua scripts
        if (!StringUtils.hasText(luaSha1)) {
            return getLuaSha1AndExecute(key, limitCount, requestPeriod);
        } else {
            try {
                return (boolean) redissonUtils.executeLuaSha(luaSha1, RScript.Mode.READ_WRITE, RScript.ReturnType.BOOLEAN, Collections.singletonList(key), limitCount, requestPeriod);
            } catch (Exception e) {
                logger.error("Lua script execution failed, SHA1：{}", luaSha1);
                return myRateLimitFallback(e, key, limitCount);
            }
        }
    }

    /**
     * Cache the Sha1 of Lua scripts and execute them, using double-checked locking to ensure thread safety
     * @param key redis key
     * @param limitCount Maximum number of visits allowed within a certain period of time
     * @param requestPeriod For a period of time
     * @return Return value of executing a Lua script
     */
    private boolean getLuaSha1AndExecute(String key, int limitCount, int requestPeriod) {
        synchronized (this) {
            if (!StringUtils.hasText(luaSha1)) {
                Resource resource = new ClassPathResource(LUA_SCRIPT_PATH);
                // The resource.getFile method cannot be used after packaging
                try (InputStream inputStream = resource.getInputStream()) {
                    // Lua generates Sha1 and caches it in Redis
                    luaSha1 = redissonUtils.cacheLua(inputStream);
                    return (boolean) redissonUtils.executeLua(inputStream, RScript.Mode.READ_WRITE, RScript.ReturnType.BOOLEAN, Collections.singletonList(key), limitCount, requestPeriod);
                } catch (IOException | RedisException e) {
                    return myRateLimitFallback(e, key, limitCount);
                }
            }
            return (boolean) redissonUtils.executeLuaSha(luaSha1, RScript.Mode.READ_WRITE, RScript.ReturnType.BOOLEAN, Collections.singletonList(key), limitCount, requestPeriod);
        }
    }


    /**
     * Current limiting downgrade method, because occasionally the sha1 value of the lua script becomes invalid
     * @param e exception
     * @param key redis key
     * @param requestPeriod Limiting the time for repeated requests
     * @return Is limit?
     */
    private boolean myRateLimitFallback(Exception e, String key, int requestPeriod) {
        if (e instanceof RedisException) {
            logger.error("Redis execute Lua exception", e);
        } else if (e instanceof IOException) {
            logger.error("Lua script reading failed", e);
        } else {
            logger.error("An unknown anomaly occurred in the rate limiting service", e);
        }
        logger.warn("Implement a simple rate limiting method");
        // Reset the SHA1 of the Lua script to trigger a reload
        luaSha1 = null;
        try {
            if (redissonUtils.hasKey(key)) {
                return true;
            } else {
                // Record the current request time in milliseconds and set an expiration time based on the time limit
                redissonUtils.setObject(key, "", Duration.ofSeconds(requestPeriod));
                return false;
            }
        } catch (Exception fallbackException) {
            logger.error("The degradation and rate limiting method also failed, allowing the request to pass", fallbackException);
            return false;
        }
    }

    /**
     * Implemented based on Redisson's built-in rate limiting method
     *
     * @param key                key
     * @param repeatRequestLimit annotation
     * @return Is limit?
     */
    private boolean redissonRateLimit(String key, RepeatRequestLimit repeatRequestLimit) {
        // Set the rate-limiting type
        RateType rateType = RateType.OVERALL;
        if (repeatRequestLimit.limitType() == RequestLimitTypeEnum.CLUSTER) {
            rateType = RateType.PER_CLIENT;
        }
        long remainCount = redissonUtils.rateLimit(key, rateType, repeatRequestLimit.count(), repeatRequestLimit.time());
        // -1 indicates rate limiting
        return remainCount == -1L;
    }

    /**
     * Depending on the different rate-limiting types, concatenate the key
     *
     * @param request   request information
     * @param limitType rate-limiting type
     * @return redis key
     */
    private String combinationKey(HttpServletRequest request, RequestLimitTypeEnum limitType) {
        String uri = request.getRequestURI();
        String identifier = switch (limitType) {
            case IP -> IpUtils.getIpAddr(request);
            case CLUSTER -> redissonUtils.getClientId();
            case DEFAULT -> request.getSession().getId();
        };
        return "rateLimit-" + identifier + "-" + uri;
    }

}
