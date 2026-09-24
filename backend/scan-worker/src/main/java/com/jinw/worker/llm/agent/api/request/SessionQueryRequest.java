package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionQueryRequest extends BaseRequest {
    private String directory;
    private String workspace;
    private String scope;        // enum: project
    private String path;
    private Object roots;        // Boolean or String ("trueFile.separatorfalse")
    private Integer start;
    private String search;
    private Integer limit;
}