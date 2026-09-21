package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorData {
    private String message;
    private Integer statusCode;
    private Boolean isRetryable;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private Map<String, String> metadata;
}