package edu.unsw.core.constant;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:44
 * @description Request path prefix, used to identify different identity modules
 */
public final class RequestPrefixConstant {
    private RequestPrefixConstant() {

    }

    public static final String GENERAL_PREFIX = "/general";
    public static final String SUPER_ADMIN_PREFIX = "/superAdmin";
    public static final String REGISTRAR_PREFIX = "/registrar";
    public static final String SUPERVISOR_PREFIX = "/supervisor";
    public static final String STUDENT_PREFIX = "/student";
}
