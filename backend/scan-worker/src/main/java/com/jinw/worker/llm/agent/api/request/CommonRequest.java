package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonRequest extends BaseRequest {
    private String directory;
    private String workspace;
}
