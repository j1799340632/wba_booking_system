package edu.unsw.service.impl;

import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.service.VerifyService;
import edu.unsw.service.utils.RandomUtils;
import edu.unsw.service.utils.mail.EmailSenderUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/7/2026 16:16
 * @description VerifyServiceImpl
 */
@Service
public class VerifyServiceImpl implements VerifyService {
    private final EmailSenderUtils emailSenderUtils;
    private final RedissonUtils redissonUtils;

    public VerifyServiceImpl(EmailSenderUtils emailSenderUtils, RedissonUtils redissonUtils) {
        this.emailSenderUtils = emailSenderUtils;
        this.redissonUtils = redissonUtils;
    }

    @Override
    public void getEmailVerificationCode() {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        String email = currentUser.getEmail();

        // 4-digit random verification code
        Integer verifyCode = Integer.valueOf(RandomUtils.getRandom(1000, 9999, 4));
        int expireMin = 3;
        String content = "You email verify code is " + verifyCode + ". Valid for " + expireMin + " minutes";
        String title = "Verification";

        // Set code expire time
        redissonUtils.setObject(email, verifyCode, Duration.ofMinutes(3));

        emailSenderUtils.sendHtmlMailByDefaultTemplate(email, title, currentUser.getName(), content);
    }
}
