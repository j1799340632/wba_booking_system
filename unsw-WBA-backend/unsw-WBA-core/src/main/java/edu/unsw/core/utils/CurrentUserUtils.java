package edu.unsw.core.utils;

import edu.unsw.core.domain.pojo.CurrentUser;

/**
 * @author 徐一杰 Yijie Xu Bubble
 * @date 2026/6/21 12:32
 * @description Stores the logged-in user for the current request
 */
public class CurrentUserUtils {
    private CurrentUserUtils() {

    }

    private static final ThreadLocal<CurrentUser> LOCAL_USER = new InheritableThreadLocal<>();

    /**
     * Set the login user information requested for the current request
     *
     * @param currentUser The currently requested login user
     */
    public static void setUser(CurrentUser currentUser) {
        LOCAL_USER.set(currentUser);
    }

    /**
     * Get the currently requested login user information
     *
     * @return The currently requested login user
     */
    public static CurrentUser getUser() {
        return LOCAL_USER.get();
    }

    /**
     * Clear the currently requested login user information
     */
    public static void remove() {
        LOCAL_USER.remove();
    }

}
