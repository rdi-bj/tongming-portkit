package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
public class TextPart extends Part {
    private String text;
    private Boolean synthetic;
    private Boolean ignored;
    private PartTime time;
    private Object metadata;
}