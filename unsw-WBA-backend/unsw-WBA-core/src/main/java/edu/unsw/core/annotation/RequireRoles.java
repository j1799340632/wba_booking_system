package edu.unsw.core.annotation;

import java.lang.annotation.*;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 23/6/2026 20:00
 * @description Checking whether the interface needs the user has the required role
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRoles {
    /**
     * The role keys allowed to access this interface (e.g., {"Admin", "PS", "Stu"})
     */
    String[] value() default {};
}
