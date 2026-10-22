package edu.unsw.core.validation;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 20:27
 * @description Classification verification, for example, adding a new user does not need to pass the value ID, but modifying it requires
 */
public interface ValidationRules {
    /**
     * Adding rules
     */
    interface Add {

    }

    /**
     * Modifying rules
     */
    interface Edit {

    }

    /**
     * Deleting rules
     */
    interface Delete {

    }

}
