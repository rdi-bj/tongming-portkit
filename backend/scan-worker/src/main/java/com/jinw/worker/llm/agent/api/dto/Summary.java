package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Summary {

    private Integer additions;
    private Integer deletions;
    private Integer files;
    private List<Diff> diffs;
}