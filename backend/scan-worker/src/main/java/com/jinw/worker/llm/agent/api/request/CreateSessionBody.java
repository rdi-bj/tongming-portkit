package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jinw.worker.llm.agent.api.dto.Model;
import com.jinw.worker.llm.agent.api.dto.Permission;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSessionBody extends BaseRequest {
    private String directory;
    // 可以不传，默认为directory
    private String workspace;
    private String parentID;
    private String title;
    private String agent;
    private Model model;
    private List<Permission> permission;
    private String workspaceID;

}
