package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jinw.worker.llm.agent.api.dto.part.Part;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageWrapper {
    private Info info;
    private List<Part> parts;
}