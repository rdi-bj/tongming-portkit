package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
public class ToolPart extends Part {
    private String callID;
    private String tool;
    private ToolState state;
    private Object metadata;
}