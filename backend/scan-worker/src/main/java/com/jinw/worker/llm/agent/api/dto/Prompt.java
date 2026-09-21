package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Prompt {
    private String text;
    private List<FileRef> files = List.of();
    private List<AgentRef> agents = List.of();
    private List<Reference> references = List.of();
}
