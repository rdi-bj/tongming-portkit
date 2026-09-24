package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Info {
    private String id;
    private String sessionID;
    private String role;
    private Time time;
    private Format format;
//    private Summary summary;
    private String agent;
    private Model model;
    private String system;
    private Map<String, Boolean> tools;
}