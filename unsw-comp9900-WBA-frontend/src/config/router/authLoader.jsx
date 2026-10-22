import { useUserStore } from "@/config/store/useUserStore.js";

/**
 * Check if the user has the required role to access the route
 * @param {Array<string|number>} allowedRoles Permissions required to access the route
 */
export const requireAuth = (allowedRoles) => {
    // react-router loader function
    return async () => {
        const userInfo = useUserStore.getState().userInfo;
        const currentRoleId = userInfo?.roleId ? String(userInfo.roleId) : null;

        // Check if the user has the required role
        const rolesStr = (allowedRoles || []).map(r => String(r));
        const hasPermission = currentRoleId && rolesStr.includes(currentRoleId);

        if (!hasPermission) {
            throw new Response("Forbidden", { status: 403 });
        }

        return null;
    };
};
