package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Time {

    private Long created;
    private Long updated;
    private Long compacting;
    private Long archived;
    private Long completed;
}