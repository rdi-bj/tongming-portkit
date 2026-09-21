package com.jinw.worker.llm.agent.api.response;

import com.jinw.worker.llm.agent.api.dto.Prompt;
import lombok.Data;

@Data
public class PromptResponse {
    /**
     * 消息序号
     */

    private Integer admittedSeq;

    /**
     * 消息ID
     */

    private String id;

    /**
     * 会话ID
     */

    private String sessionID;

    /**
     * 用户提交的Prompt
     */

    private Prompt prompt;

    /**
     * steer / queue
     */

    private String delivery;

    /**
     * 创建时间
     */

    private Long timeCreated;
}
