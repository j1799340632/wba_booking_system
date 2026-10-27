package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Numeric application observations only; never contains a prompt, result text, session details or credentials. */
@Getter @Setter
@TableName("assistant_event")
public class AssistantEvent {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private Long actorId;
    private LocalDateTime createdAt;
    private String stage;
    private String outcome;
    private String action;
    private Long elapsedMs;
    private Integer alternativeGroups;
    private Integer modelCalls;
    private String modelName;
    private Boolean usageKnown;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
}
