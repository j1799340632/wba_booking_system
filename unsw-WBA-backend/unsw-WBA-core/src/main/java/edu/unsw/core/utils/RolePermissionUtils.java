package edu.unsw.core.utils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.constant.UserRoleKeyConstant;
import edu.unsw.core.entity.UserRole;
import edu.unsw.core.mapper.UserRoleMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 30/7/2026 12:48
 * @description Constructing a role-based permission tree
 */
@Component
public class RolePermissionUtils {
    private final Logger logger = LoggerFactory.getLogger(RolePermissionUtils.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Define role inclusion matrix (use Map to maintain hierarchical relationships)
     * Key: User role
     * Value: The set of accessible roles
     */
    private final Map<String, Set<String>> roleHierarchyMap = new HashMap<>();

    /**
     * Path-role mapping matrix (used as a fallback for paths without annotations)
     * LinkedHashMap guarantees the order of path matching (matching from top to bottom to prevent wildcard conflicts)
     */
    private final Map<String, List<String>> pathRoleMap = new LinkedHashMap<>();

    private final UserRoleMapper userRoleMapper;

    public RolePermissionUtils(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
        initPresetPathRoleMap();
    }

    /**
     * Initialize the preset path role map
     */
    private void initPresetPathRoleMap() {
        pathRoleMap.put(RequestPrefixConstant.SUPER_ADMIN_PREFIX + "/**", List.of(UserRoleKeyConstant.SUPER_ADMIN));
        pathRoleMap.put(RequestPrefixConstant.REGISTRAR_PREFIX + "/**", List.of(UserRoleKeyConstant.ADMIN));
        pathRoleMap.put(RequestPrefixConstant.SUPERVISOR_PREFIX + "/**", List.of(UserRoleKeyConstant.PLACEMENT_SUPERVISOR));
        pathRoleMap.put(RequestPrefixConstant.STUDENT_PREFIX + "/**", List.of(UserRoleKeyConstant.STUDENT));
        logger.info("The path role map tree has been constructed: {}", pathRoleMap);
    }

    @PostConstruct
    public void init() {
        initRoleTree();
    }

    public void initRoleTree() {
        List<UserRole> userRoleList = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getIsEnable, true)
        );

        if (userRoleList.isEmpty()) {
            initPresetRoleTree();
            return;
        }

        Map<String, Set<String>> tempMap = new HashMap<>();
        for (UserRole role : userRoleList) {
            Set<String> subRoleKeySet = userRoleList.stream()
                    .filter(sub -> sub.getPermissionPriority() >= role.getPermissionPriority())
                    .map(UserRole::getRoleKey)
                    .collect(Collectors.toSet());

            tempMap.put(role.getRoleKey(), subRoleKeySet);
        }

        roleHierarchyMap.putAll(tempMap);

        logger.info("The role permission tree has been constructed: {}", roleHierarchyMap);
    }


    /**
     * Get the path role map
     *
     * @return path role map
     */
    public Map<String, List<String>> getUnmodifiablePathRoleMap() {
        return Collections.unmodifiableMap(pathRoleMap);
    }

    /**
     * Expose instance methods for interceptors to call
     *
     * @return unmodifiable map of role hierarchy
     */
    public Map<String, Set<String>> getUnmodifiableRoleHierarchy() {
        return Collections.unmodifiableMap(roleHierarchyMap);
    }

    /**
     * Get the sub role set of a given role
     *
     * @param userRoleKey role key
     * @return sub role set
     */
    public Set<String> getSubRoleSet(String userRoleKey) {
        return new LinkedHashSet<>(roleHierarchyMap.getOrDefault(userRoleKey, Collections.emptySet()));
    }

    /**
     * Determine if a given role has the permissions of its child roles
     *
     * @param userRoleKey role key
     * @param requiredRoles required roles
     * @return true if the user has the required role, false otherwise
     */
    public boolean hasPermission(String userRoleKey, String[] requiredRoles) {
        Set<String> accessibleRoles = roleHierarchyMap.getOrDefault(userRoleKey, Collections.emptySet());
        return Arrays.stream(requiredRoles).anyMatch(accessibleRoles::contains);
    }

    /**
     * Calculate the required role based on the request path
     *
     * @param requestPath request path
     * @return required role
     */
    public List<String> calculateRequiredRole(String requestPath) {
        for (Map.Entry<String, List<String>> entry : getUnmodifiablePathRoleMap().entrySet()) {
            if (pathMatcher.match(entry.getKey(), requestPath)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /**
     * Initialize the preset role tree
     */
    private void initPresetRoleTree() {
        // Super Admin
        roleHierarchyMap.put(UserRoleKeyConstant.SUPER_ADMIN,
                Set.of(
                        UserRoleKeyConstant.SUPER_ADMIN,
                        UserRoleKeyConstant.ADMIN,
                        UserRoleKeyConstant.PLACEMENT_SUPERVISOR,
                        UserRoleKeyConstant.STUDENT
                ));
        // Admin
        roleHierarchyMap.put(UserRoleKeyConstant.ADMIN,
                Set.of(
                        UserRoleKeyConstant.ADMIN,
                        UserRoleKeyConstant.PLACEMENT_SUPERVISOR,
                        UserRoleKeyConstant.STUDENT
                ));
        // Placement Supervisor
        roleHierarchyMap.put(UserRoleKeyConstant.PLACEMENT_SUPERVISOR,
                Set.of(
                        UserRoleKeyConstant.PLACEMENT_SUPERVISOR,
                        UserRoleKeyConstant.STUDENT
                ));
        // Student
        roleHierarchyMap.put(UserRoleKeyConstant.STUDENT,
                Set.of(
                        UserRoleKeyConstant.STUDENT
                ));
    }
}
