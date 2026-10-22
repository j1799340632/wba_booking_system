package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.RolePermissionUtils;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.core.mapper.UserRoleMapper;
import edu.unsw.domain.dto.UserRoleQueryDto;
import edu.unsw.domain.entity.User;
import edu.unsw.core.entity.UserRole;
import edu.unsw.domain.vo.UserRoleVo;
import edu.unsw.service.UserRoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 16:09
 * @description User role service implementations
 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {
    private final UserMapper userMapper;
    private final RolePermissionUtils rolePermissionUtils;

    public UserRoleServiceImpl(UserMapper userMapper, RolePermissionUtils rolePermissionUtils) {
        this.userMapper = userMapper;
        this.rolePermissionUtils = rolePermissionUtils;
    }

    @Override
    public IPage<UserRoleVo> getRoleList(UserRoleQueryDto userRoleQueryDto) {
        String keyword = userRoleQueryDto.getKeyword();
        boolean isFilter = StringUtils.hasText(keyword);

        Page<UserRole> userRolePage = this.page(
                new Page<>(userRoleQueryDto.getPageNum(), userRoleQueryDto.getPageQuantity()),
                Wrappers.<UserRole>lambdaQuery()
                        .like(isFilter, UserRole::getRoleName, keyword)
                        .or()
                        .like(isFilter, UserRole::getRoleKey, keyword)
        );

        return userRolePage.convert(this::buildUserRoleVo);
    }

    private UserRoleVo buildUserRoleVo(UserRole userRole) {
        UserRoleVo userRoleVo = new UserRoleVo();
        BeanUtils.copyProperties(userRole, userRoleVo);

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getId, userRole.getCreator())
                        .select(User::getName)
        );

        if (Objects.nonNull(user)) {
            userRoleVo.setCreatorName(user.getName());
        }

        return userRoleVo;
    }

    @Override
    public List<UserRole> getEnabledRoleList() {
        return this.list(
                Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getIsEnable, true)
                        .select(UserRole::getId, UserRole::getPermissionPriority, UserRole::getRoleKey, UserRole::getRoleName)
        );
    }

    @Override
    public Boolean addRole(UserRole userRole) {
        checkDuplicateRoleKey(userRole.getRoleKey());

        boolean saved = this.save(userRole);

        // Reconstruct role tree
        rolePermissionUtils.initRoleTree();

        return saved;
    }

    @Override
    public Boolean editRole(UserRole userRole) {
        checkBaseRole(userRole.getId());
        checkDuplicateRoleKey(userRole.getRoleKey());

        boolean updated = this.updateById(userRole);

        // Reconstruct role tree
        rolePermissionUtils.initRoleTree();

        return updated;
    }

    @Override
    public Boolean delRole(Long id) {
        checkBaseRole(id);

        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getRoleId, id)
        );

        if (count > 0) {
            throw new BusinessException(ResponseEnum.USER_ROLE_IN_USE);
        }

        boolean removed = this.removeById(id);

        // Reconstruct role tree
        rolePermissionUtils.initRoleTree();

        return removed;
    }

    @Override
    public Boolean changeRoleStatus(Long id, Boolean targetStatus) {
        checkBaseRole(id);

        boolean updated = this.update(
                Wrappers.<UserRole>lambdaUpdate()
                        .eq(UserRole::getId, id)
                        .set(UserRole::getIsEnable, targetStatus)
        );

        // Reconstruct role tree
        rolePermissionUtils.initRoleTree();

        return updated;
    }

    private void checkBaseRole(Long roleId) {
        if (roleId == null) {
            throw new BusinessException("Role id cannot be null");
        }

        List<Long> baseRoleIdList = List.of(1L, 2L, 3L, 4L);
        if (baseRoleIdList.contains(roleId)) {
            throw new BusinessException(ResponseEnum.BASE_USER_ROLE_CANNOT_MODIFY);
        }
    }

    private void checkDuplicateRoleKey(String roleKey) {
        UserRole one = this.getOne(
                Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getRoleKey, roleKey)
        );
        if (Objects.nonNull(one)) {
            throw new BusinessException(ResponseEnum.USER_ROLE_KEY_EXIST);
        }
    }
}
