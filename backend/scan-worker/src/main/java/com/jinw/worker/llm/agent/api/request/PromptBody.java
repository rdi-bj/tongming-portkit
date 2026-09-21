package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jinw.worker.llm.agent.api.dto.Prompt;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptBody extends BaseRequest {
    private Prompt prompt;
    private String delivery = "steer";
}
