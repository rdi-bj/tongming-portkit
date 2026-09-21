package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Session {

    private String id;
    private String slug;
    private String projectID;
    private String workspaceID;
    private String directory;
    private String path;
    private String parentID;

    private Summary summary;
    private Double cost;

    private Tokens tokens;
    private Share share;

    private String title;
    private String agent;
    private Model model;
    private String version;

    private Time time;
    private List<Permission> permission;

    private Revert revert;
}