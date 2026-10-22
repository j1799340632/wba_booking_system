package edu.unsw.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.domain.dto.UserRoleQueryDto;
import edu.unsw.core.entity.UserRole;
import edu.unsw.domain.vo.UserRoleVo;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 16:08
 * @description User role services
 */
public interface UserRoleService extends IService<UserRole> {
    /**
     * Get role list
     *
     * @param userRoleQueryDto User role query
     * @return Page of user roles
     */
    IPage<UserRoleVo> getRoleList(UserRoleQueryDto userRoleQueryDto);

    /**
     * Get enabled role list
     *
     * @return List of enabled user roles
     */
    List<UserRole> getEnabledRoleList();

    /**
     * Add a role
     *
     * @param userRole User role
     * @return Is add successful?
     */
    Boolean addRole(UserRole userRole);

    /**
     * Edit role (can only edit role name)
     *
     * @param userRole User role info
     * @return Is edit successful?
     */
    Boolean editRole(UserRole userRole);

    /**
     * Delete a role
     *
     * @param id User role ID
     * @return Is delete successful?
     */
    Boolean delRole(Long id);

    /**
     * Change a role status
     *
     * @param id User role ID
     * @param targetStatus Target status
     * @return Is change successful?
     */
    Boolean changeRoleStatus(Long id, Boolean targetStatus);
}
