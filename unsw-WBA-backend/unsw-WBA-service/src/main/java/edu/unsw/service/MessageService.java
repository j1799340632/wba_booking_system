package edu.unsw.service;

import com.baomidou.mybatisplus.spring.service.IService;
import edu.unsw.domain.entity.Message;
import edu.unsw.domain.vo.MessageVo;

import java.util.List;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 16/7/2026 14:52
 * @description Message service
 */
public interface MessageService extends IService<Message> {
    /**
     * Get message list
     *
     * @return messageVo list
     */
    List<MessageVo> getMessageList();

    /**
     * Save message
     *
     * @param message message entity
     * @return message entity
     */
    Message saveMessage(Message message);

    /**
     * Read messages
     *
     * @param ids message ids
     */
    void readMessages(Long[] ids);
}
