package edu.unsw.core.interceptor;

import edu.unsw.core.annotation.RequireRoles;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RolePermissionUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 23/6/2026 20:04
 * @description Checking whether the user has the required permission
 */
@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final RolePermissionUtils rolePermissionUtils;

    public PermissionInterceptor(RolePermissionUtils rolePermissionUtils) {
        this.rolePermissionUtils = rolePermissionUtils;
    }

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) throws Exception {
        // Determine if the request is from a Controller
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Annotation on the method is priority
        RequireRoles requireRolesAnnotation = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (Objects.isNull(requireRolesAnnotation)) {
            // Get annotation on Controller Class
            requireRolesAnnotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequireRoles.class);
        }

        // Get the request path（e.g., /student/user/update）
        String requestPath = request.getRequestURI();

        // The minimum requirement to access the interface
        String[] requiredRoles;

        if (Objects.nonNull(requireRolesAnnotation)) {
            // Meeting any one of the requirements is enough
            requiredRoles = requireRolesAnnotation.value();
        } else {
            // If no annotation is specified, path matching will be used
            requiredRoles = rolePermissionUtils.calculateRequiredRole(requestPath).toArray(new String[0]);
        }

        // If there are no annotations and the path does not match any rules, it is a fully public interface and should be allowed directly
        if (Objects.isNull(requiredRoles) || requiredRoles.length == 0) {
            return true;
        }

        // Get the login user information from the session
        CurrentUser currentUser = CurrentUserUtils.getUser();

        // The role ket of the login user
        String myRealRole = currentUser.getRoleKey();

        // Get the set of all permissions that this role can request.
        boolean hasPermission = rolePermissionUtils.hasPermission(myRealRole, requiredRoles);

        // Does reachableRoles contain the minimum role required by the interface?
        if (!hasPermission) {
            throw new BusinessException(ResponseEnum.NO_PERMISSION);
        }

        return true;
    }
}
