package edu.unsw.core.interceptor;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * @author 徐一杰 Yijie Xu Bubble
 * @date 2026/6/21 12:20
 * @description Login interceptor
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final RedissonUtils redissonUtils;

    public LoginInterceptor(RedissonUtils redissonUtils) {
        this.redissonUtils = redissonUtils;
    }

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        // Determine if the request is from a Controller
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResponseEnum.NOT_LOGGED_IN);
        }

        String token = authHeader.substring(7).trim();

        String redisKey = RedisKeyConstant.TOKEN_KEY_PREFIX + token;
        CurrentUser currentUser = redissonUtils.getObject(redisKey);

        if (Objects.isNull(currentUser)) {
            throw new BusinessException(ResponseEnum.NOT_LOGGED_IN);
        }

        CurrentUserUtils.setUser(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler, @Nullable Exception ex) throws Exception {
        CurrentUserUtils.remove();
    }
}
