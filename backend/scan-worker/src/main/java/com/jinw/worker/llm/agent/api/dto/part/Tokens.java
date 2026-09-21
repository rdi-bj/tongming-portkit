package com.jinw.worker.llm.agent.api.dto.part;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tokens {
    private Integer total;
    private Integer input;
    private Integer output;
    private Integer reasoning;
    private Cache cache;
}