package com.jinw.common.domain;

import lombok.Data;

import java.util.List;

@Data
public class LlmTaskMessage {

    private String taskId;

    private String fileId;

    private String projectPath;

    private String questionFilePath;

    private List<LlmQuestionInfo> questionInfoList;

    private String sessionId;

    private LlmConfig llmConfig;

}