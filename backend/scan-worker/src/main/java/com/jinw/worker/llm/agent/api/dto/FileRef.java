package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileRef {
    private String uri;
    private String mime;
    private String name;
}