package edu.unsw.service.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.redisson.AbstractStreamConsumer;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.dao.mapper.UserSettingsMapper;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.Message;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.entity.UserSettings;
import edu.unsw.service.MessageService;
import edu.unsw.service.utils.mail.EmailSenderUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/7/2026 18:59
 * @description The implementation class of concurrent preemptive consumer for system notification messages in Stream
 */
@Component
public class NotificationMessageConsumer extends AbstractStreamConsumer<NotificationDto> {
    private final MessageService messageService;
    private final RedissonUtils redissonUtils;
    private final UserMapper userMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final EmailSenderUtils emailSenderUtils;

    protected NotificationMessageConsumer(RedissonUtils redissonUtils, Environment environment, MessageService messageService, UserMapper userMapper, UserSettingsMapper userSettingsMapper, EmailSenderUtils emailSenderUtils) {
        this.messageService = messageService;
        this.redissonUtils = redissonUtils;
        this.userMapper = userMapper;
        this.emailSenderUtils = emailSenderUtils;
        super(RedisKeyConstant.MESSAGE_STREAM_KEY, RedisKeyConstant.MESSAGE_STREAM_PAYLOAD_KEY, RedisKeyConstant.MESSAGE_STREAM_GROUP_SAVE, redissonUtils, environment);
        this.userSettingsMapper = userSettingsMapper;
    }

    @Override
    protected void onMessage(NotificationDto notificationDto) {
        Message message = new Message();

        // Save notification to database
        Long senderId = notificationDto.getSenderId();
        Long receiverId = notificationDto.getReceiverId();
        // If sender is null, it indicates that the message is sent by the system
        if (Objects.nonNull(senderId)) {
            message.setCreator(senderId);
            message.setReceiver(receiverId);
            message.setContent(notificationDto.getEventContent());
            messageService.saveMessage(message);
        }

        notificationDto.setMessageId(message.getId());

        // Send email
        try {
            if (Boolean.TRUE.equals(notificationDto.getIsSendEmail())) {
                UserSettings userSettings = userSettingsMapper.selectOne(
                        Wrappers.<UserSettings>lambdaQuery()
                                .eq(UserSettings::getUserId, receiverId)
                );

                if (Objects.nonNull(userSettings) && Boolean.TRUE.equals(userSettings.getIsReceiveEmail())) {
                    User user = userMapper.selectOne(
                            Wrappers.<User>lambdaQuery()
                                    .eq(User::getId, receiverId)
                                    .select(User::getEmail, User::getName)
                    );

                    if (Objects.nonNull(user)) {
                        emailSenderUtils.sendHtmlMailByDefaultTemplate(user.getEmail(), notificationDto.getEventName(), user.getName(), notificationDto.getEventContent());
                    }
                }

            }
        } catch (Exception e) {
            logger.error("Failed to send email notification: {}", e.getMessage());
        }

        redissonUtils.publish(RedisKeyConstant.SSE_NOTIFY_CHANNEL, notificationDto);
    }
}
