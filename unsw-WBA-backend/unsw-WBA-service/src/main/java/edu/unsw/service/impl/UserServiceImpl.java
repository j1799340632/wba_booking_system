package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.core.utils.RolePermissionUtils;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.core.mapper.UserRoleMapper;
import edu.unsw.domain.dto.UserQueryDto;
import edu.unsw.domain.dto.UserLoginDto;
import edu.unsw.domain.dto.UserResetOwnPasswordDto;
import edu.unsw.domain.dto.UserResetPasswordDto;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.core.entity.UserRole;
import edu.unsw.domain.vo.UserVo;
import edu.unsw.service.UserService;
import edu.unsw.service.constant.RegexPatternConstant;
import edu.unsw.service.utils.FastExcelUtils;
import edu.unsw.service.utils.mail.EmailSenderUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 20:12
 * @description User-related services implementation
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(RegexPatternConstant.EMAIL_REGEX);

    private final UserRoleMapper userRoleMapper;
    private final RedissonUtils redissonUtils;
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final EmailSenderUtils emailSenderUtils;
    private final UserMapper userMapper;
    private final RolePermissionUtils rolePermissionUtils;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRoleMapper userRoleMapper, RedissonUtils redissonUtils, AssessmentSlotMapper assessmentSlotMapper, EmailSenderUtils emailSenderUtils, UserMapper userMapper, RolePermissionUtils rolePermissionUtils, PasswordEncoder passwordEncoder) {
        this.userRoleMapper = userRoleMapper;
        this.redissonUtils = redissonUtils;
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.emailSenderUtils = emailSenderUtils;
        this.userMapper = userMapper;
        this.rolePermissionUtils = rolePermissionUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserVo login(UserLoginDto userLoginDto) {
        // Retrieve the user from the database which is not deleted
        User user = new LambdaQueryChainWrapper<>(User.class)
                .eq(User::getEmail, userLoginDto.getEmail())
                .eq(User::getIsDelete, false)
                .one();

        // Check the login result
        ResponseEnum errorEnum = checkLogin(userLoginDto, user);

        // If the return error code is not empty, it means that the login failed, and an exception is thrown
        if (Objects.nonNull(errorEnum)) {
            throw new BusinessException(errorEnum);
        }

        // Login successful, query user role information
        UserVo userVo = compositeUserVo(user);
        String token = UUID.randomUUID().toString().replace("-", "");
        userVo.setToken(token);

        // Save login user information
        CurrentUser currentUser = new CurrentUser();
        BeanUtils.copyProperties(userVo, currentUser);
        redissonUtils.setObject(RedisKeyConstant.TOKEN_KEY_PREFIX + token, currentUser, Duration.ofHours(24));

        return userVo;
    }

    /**
     * Check login result
     *
     * @param userLoginDto User login information from the frontend
     * @param user User information in the database
     * @return Error code
     */
    private ResponseEnum checkLogin(UserLoginDto userLoginDto, User user) {
        if (Objects.isNull(user)) {
            return ResponseEnum.ACCOUNT_NOT_EXIST;
        } else if (Boolean.FALSE.equals(user.getIsEnable())) {
            return ResponseEnum.ACCOUNT_BLOCKED;
        }  else if (Boolean.TRUE.equals(user.getIsCanceled())) {
            return ResponseEnum.ACCOUNT_CANCELED;
        } else if (passwordEncoder.matches(userLoginDto.getPassword(), user.getPassword())) {
            // Update the last login time
            user.setLastLoginTime(LocalDateTime.now(ZoneId.systemDefault()));
            this.updateById(user);
        } else {
            return ResponseEnum.PASSWORD_ERROR;
        }
        return null;
    }

    private UserVo compositeUserVo(User user) {
        if (Objects.isNull(user)) {
            return null;
        }
        UserRole userRole = userRoleMapper.selectById(user.getRoleId());
        // Query user role information
        if (Objects.isNull(userRole)) {
            throw new BusinessException(ResponseEnum.USER_ROLE_NOT_FOUND);
        }

        // Compose userVo information
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        userVo.setRoleId(userRole.getId());
        userVo.setPermissionPriority(userRole.getPermissionPriority());
        userVo.setRoleName(userRole.getRoleName());
        userVo.setRoleKey(userRole.getRoleKey());
        return userVo;
    }

    @Override
    public CurrentUser loginOut() {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        redissonUtils.deleteObject(RedisKeyConstant.TOKEN_KEY_PREFIX + currentUser.getToken());
        return currentUser;
    }

    @Override
    public UserVo updateProfile(User user) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        Long userId = currentUser.getId();

        checkDuplicateEmail(userId, user.getEmail());

        user.setId(userId);
        this.updateById(user);
        user = this.getById(user.getId());

        UserVo userVo = new UserVo();

        // It indicates that the user role has been modified, so the user role information needs to be queried again
        if (!Objects.equals(currentUser.getRoleId(), user.getRoleId())) {
            userVo = compositeUserVo(user);
        } else {
            BeanUtils.copyProperties(user, userVo);
            userVo.setRoleId(currentUser.getRoleId());
            userVo.setRoleKey(currentUser.getRoleKey());
        }

        BeanUtils.copyProperties(userVo, currentUser);

        return userVo;
    }

    @Override
    public UserVo updateOtherProfile(User user) {
        Long targetUserId = user.getId();
        checkDuplicateEmail(targetUserId, user.getEmail());

        this.updateById(user);

        user = this.getById(targetUserId);
        return compositeUserVo(user);
    }

    private void checkDuplicateEmail(Long targetUserId, String email) {
        User one = this.getOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getEmail, email)
                        .ne(User::getId, targetUserId)
        );
        if (Objects.nonNull(one)) {
            throw new BusinessException(ResponseEnum.ACCOUNT_EXIST);
        }
    }

    @Override
    public UserVo getProfile(Long id) {
        // If null, get the currently logged-in user
        if (Objects.isNull(id)) {
            id = CurrentUserUtils.getUser().getId();
        }

        User user = this.getById(id);
        if (Objects.isNull(user)) {
            throw new BusinessException(ResponseEnum.ACCOUNT_NOT_EXIST);
        }

        return compositeUserVo(user);
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void updateAvailableStatus(Boolean isAvailable) {
        Long userId = CurrentUserUtils.getUser().getId();
        User user = new User();
        user.setIsAvailable(isAvailable);
        user.setId(userId);
        this.updateById(user);

        assessmentSlotMapper.update(
                Wrappers.<AssessmentSlot>lambdaUpdate()
                        .eq(AssessmentSlot::getCreator, userId)
                        .set(AssessmentSlot::getIsEnable, isAvailable)
        );
    }

    @Override
    public void resetOwnPassword(UserResetOwnPasswordDto userResetOwnPasswordDto) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        handleResetPassword(currentUser, userResetOwnPasswordDto.getVerifyCode(), userResetOwnPasswordDto.getNewPassword());
    }

    @Override
    public void resetPassword(UserResetPasswordDto userResetPasswordDto) {
        CurrentUser currentUser = new CurrentUser();
        // Reset target user password
        currentUser.setId(userResetPasswordDto.getTargetUserId());
        // Verify current admin user email
        currentUser.setEmail(CurrentUserUtils.getUser().getEmail());

        handleResetPassword(currentUser, userResetPasswordDto.getVerifyCode(), userResetPasswordDto.getNewPassword());
    }

    private void handleResetPassword(CurrentUser currentUser, Integer userVerifyCode, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException("Password is empty");
        }

        String email = currentUser.getEmail();
        Long userId = currentUser.getId();
        Integer verificationCode = redissonUtils.getObject(email);

        if (Objects.isNull(verificationCode)) {
            throw new BusinessException(ResponseEnum.VERIFICATION_EXPIRED);
        } else if (!Objects.equals(userVerifyCode, verificationCode)) {
            throw new BusinessException(ResponseEnum.VERIFICATION_ERROR);
        }

        User user = this.getById(userId);
        if (Objects.isNull(user)) {
            throw new BusinessException(ResponseEnum.ACCOUNT_NOT_EXIST);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ResponseEnum.PASSWORD_CONFLICT);
        }

        this.update(
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, userId)
                        .set(User::getPassword, passwordEncoder.encode(newPassword))
        );

        redissonUtils.deleteObject(email);

        // Notify associated user
        emailSenderUtils.sendHtmlMailByDefaultTemplate(user.getEmail(), "Notification", user.getName(), "You password has been changed");
    }

    @Override
    public IPage<UserVo> getUserList(UserQueryDto userQueryDto) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        Set<String> subRoleSet = rolePermissionUtils.getSubRoleSet(currentUser.getRoleKey());

        String name = userQueryDto.getName();
        String hospital = userQueryDto.getHospital();
        String department = userQueryDto.getDepartment();

        List<UserRole> userRoleList = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery()
                        .in(UserRole::getRoleKey, subRoleSet)
                        .select(UserRole::getId)
        );

        IPage<User> page = new Page<>(userQueryDto.getPageNum(), userQueryDto.getPageQuantity());

        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper
                .eq(User::getIsCanceled, false)
                .eq(StringUtils.hasLength(hospital), User::getHospital, hospital)
                .eq(StringUtils.hasLength(department), User::getDepartment, department)
                .like(StringUtils.hasLength(name), User::getName, name)
                .in(!userRoleList.isEmpty(), User::getRoleId, userRoleList.stream().map(UserRole::getId).toList())
                .orderByDesc(User::getId);

        IPage<User> userPage = this.page(page, queryWrapper);
        return userPage.convert(this::compositeUserVo);
    }

    @Override
    public User addUser(User user) {
        checkRoleOfAddUser(user.getRoleId());

        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("Password is empty");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Check duplicate email
        User one = this.getOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getEmail, user.getEmail())
        );

        if (Objects.nonNull(one)) {
            // Resume user
            if (Boolean.TRUE.equals(one.getIsCanceled())) {
                user.setId(one.getId());
                user.setIsCanceled(false);
                this.updateById(user);
                return user;
            } else {
                throw new BusinessException(ResponseEnum.ACCOUNT_EXIST);
            }
        }

        this.save(user);

        return user;
    }

    @Override
    public Boolean changeUserStatus(Long userId, Boolean targetStatus) {
        return this.update(
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, userId)
                        .set(User::getIsEnable, targetStatus)
        );
    }

    @Override
    public Integer importBatchUser(MultipartFile multipartFile) {
        List<User> userList = new ArrayList<>();
        int insertCount = 0;

        try (InputStream inputStream = multipartFile.getInputStream()) {
            // Parse to Header-Value map list
            List<Map<String, String>> dataList = FastExcelUtils.readToMapList(inputStream);

            for (Map<String, String> rowMap : dataList) {
                User user = new User();

                user.setName(rowMap.get("Name"));
                String email = rowMap.get("Email");
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode(rowMap.get("Password")));
                String roleKey = rowMap.get("RoleKey");

                // Check email format
                if (Objects.isNull(email) || !EMAIL_PATTERN.matcher(email).matches()) {
                    logger.error("Invalid email format: {}", email);
                    continue;
                }

                // Check role key
                try {
                    Long roleId = checkRoleOfAddUser(roleKey);
                    user.setRoleId(roleId);
                } catch (Exception _) {
                    continue;
                }

                // Check duplicate email
                User one = this.getOne(
                        Wrappers.<User>lambdaQuery()
                                .eq(User::getEmail, user.getEmail())
                );

                if (Objects.nonNull(one)) {
                    // Resume user
                    if (Boolean.TRUE.equals(one.getIsCanceled())) {
                        user.setId(one.getId());
                        user.setIsCanceled(false);
                        this.updateById(user);
                        insertCount++;
                    } else {
                        logger.error("Email {} exist, skip this user", email);
                    }
                    continue;
                }

                userList.add(user);
                insertCount++;
            }

            userMapper.insert(userList);

            return insertCount;
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private void checkRoleOfAddUser(Long addUserRoleId) {
        UserRole userRole = userRoleMapper.selectById(addUserRoleId);
        if (Objects.isNull(userRole)) {
            throw new BusinessException(ResponseEnum.USER_ROLE_NOT_FOUND);
        }

        checkRoleOfAddUser(userRole);
    }

    private Long checkRoleOfAddUser(String addUserRoleKey) {
        UserRole userRole = userRoleMapper.selectOne(
                Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getRoleKey, addUserRoleKey)
        );

        if (Objects.isNull(userRole)) {
            logger.error("RoleKey {} not found, skip this user", addUserRoleKey);
            throw new BusinessException(ResponseEnum.USER_ROLE_NOT_FOUND);
        }

        checkRoleOfAddUser(userRole);

        return userRole.getId();
    }

    private void checkRoleOfAddUser(UserRole userRole) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        UserRole currentUserRole = userRoleMapper.selectById(currentUser.getRoleId());
        if (userRole.getPermissionPriority() < currentUserRole.getPermissionPriority()) {
            logger.error("You can't add user with higher permission");
            throw new BusinessException(ResponseEnum.NO_PERMISSION.getCode(), "You can't add user with higher permission");
        }
    }

    @Override
    public Boolean delUser(Long targetUserId) {
        return this.update(
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, targetUserId)
                        .set(User::getIsCanceled, true)
        );
    }

    @Override
    public void downloadUserImportTemplate(HttpServletResponse response) {
        // Get available role key list
        List<String> roleKeyList = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery()
                        .select(UserRole::getRoleKey)
                        .eq(UserRole::getIsEnable, true)
        ).stream().map(UserRole::getRoleKey).toList();

        FastExcelUtils.generateUserImportTemplate(response, roleKeyList);
    }
}
