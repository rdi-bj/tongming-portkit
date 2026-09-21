package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubtaskModel {
    private String providerID;
    private String modelID;
}