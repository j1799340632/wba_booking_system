package edu.unsw.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.domain.dto.UserQueryDto;
import edu.unsw.domain.dto.UserLoginDto;
import edu.unsw.domain.dto.UserResetOwnPasswordDto;
import edu.unsw.domain.dto.UserResetPasswordDto;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.UserVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/6/2026 20:12
 * @description User-related services
 */
public interface UserService extends IService<User> {

    /**
     * Login
     *
     * @param userLoginDto User information
     * @return User information
     */
    UserVo login(UserLoginDto userLoginDto);

    /**
     * Logout
     *
     * @return User information
     */
    CurrentUser loginOut();

    /**
     * Update user information
     *
     * @param user User information
     * @return User information
     */
    UserVo updateProfile(User user);

    /**
     * Update other users' information
     *
     * @param user User information
     * @return User information
     */
    UserVo updateOtherProfile(User user);

    /**
     * Get user information
     *
     * @param id User id (if null, get the currently logged-in user)
     * @return User information
     */
    UserVo getProfile(Long id);

    /**
     * Update if user available to receive bookings
     *
     * @param isAvailable Is available to receive bookings
     */
    void updateAvailableStatus(Boolean isAvailable);

    /**
     * Reset own password
     *
     * @param userResetOwnPasswordDto Password and verification information
     */
    void resetOwnPassword(UserResetOwnPasswordDto userResetOwnPasswordDto);

    /**
     * Reset password
     *
     * @param userResetPasswordDto Password and verification information
     */
    void resetPassword(UserResetPasswordDto userResetPasswordDto);

    /**
     * Get student and supervisor list by page
     *
     * @param userQueryDto query dto
     * @return user list
     */
    IPage<UserVo> getUserList(UserQueryDto userQueryDto);

    /**
     * Add user
     *
     * @param user User information
     * @return User information
     */
    User addUser(User user);

    /**
     * Change the user status
     *
     * @param userId User id
     * @param targetStatus Target status
     * @return Whether the operation is successful
     */
    Boolean changeUserStatus(Long userId, Boolean targetStatus);

    /**
     * Import batch user
     *
     * @param multipartFile file
     * @return Import number
     */
    Integer importBatchUser(MultipartFile multipartFile);

    /**
     * Del user
     *
     * @param targetUserId User id
     * @return Whether the operation is successful
     */
    Boolean delUser(Long targetUserId);

    /**
     * Download a user import template
     *
     * @param response response
     */
    void downloadUserImportTemplate(HttpServletResponse response);
}
