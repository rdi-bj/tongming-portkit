package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jinw.worker.llm.agent.api.dto.Part;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptAsyncBody extends BaseRequest {
    private String messageID;
    private String agent;
    private List<Part> parts;
}
