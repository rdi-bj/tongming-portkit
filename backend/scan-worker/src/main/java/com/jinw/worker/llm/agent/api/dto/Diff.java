package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Diff {

    private String file;
    private String patch;
    private Integer additions;
    private Integer deletions;
    private String status; // added / modified / deleted
}