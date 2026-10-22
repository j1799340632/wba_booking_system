package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.dao.mapper.MessageMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.entity.Message;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.MessageVo;
import edu.unsw.service.MessageService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:53
 * @description Message service implementation
 */
@Service
public class MessageImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
    private final UserMapper userMapper;

    public MessageImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<MessageVo> getMessageList() {
        // Only display the latest 100 messages
        Page<Message> messagePage = this.page(
                new Page<>(1, 100),
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getReceiver, CurrentUserUtils.getUser().getId())
                        .orderByDesc(Message::getId)
        );
        return buildMessageVo(messagePage.getRecords());
    }

    private List<MessageVo> buildMessageVo(List<Message> messageList) {
        if (messageList.isEmpty()) {
            return Collections.emptyList();
        }
        messageList.sort(Comparator.comparing(Message::getCreator));
        Set<Long> creatorIdList = messageList.stream().map(Message::getCreator).collect(Collectors.toSet());
        List<User> creatorList = userMapper.selectList(Wrappers.<User>lambdaQuery().in(User::getId, creatorIdList));

        List<MessageVo> messageVoList = new ArrayList<>();
        messageList.forEach(message -> {
            MessageVo messageVo = new MessageVo();
            BeanUtils.copyProperties(message, messageVo);
            Optional<User> first = creatorList.stream().filter(user -> user.getId().equals(message.getCreator())).findFirst();
            if (first.isPresent()) {
                User creator = first.get();
                messageVo.setCreatorName(creator.getName());
                messageVo.setCreatorAvatar(creator.getAvatar());
            }
            messageVoList.add(messageVo);
        });
        return messageVoList;
    }

    @Override
    public Message saveMessage(Message message) {
        this.save(message);
        return message;
    }

    @Override
    public void readMessages(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        this.update(
                Wrappers.<Message>lambdaUpdate()
                        .set(Message::getIsRead, true)
                        .set(Message::getReadTime, LocalDateTime.now(ZoneId.systemDefault()))
                        .in(Message::getId, List.of(ids))
        );
    }
}
