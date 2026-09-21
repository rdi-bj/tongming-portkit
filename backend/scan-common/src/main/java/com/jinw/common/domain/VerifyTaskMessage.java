package com.jinw.common.domain;

import lombok.Data;

import java.util.List;

@Data
public class VerifyTaskMessage {

    private String taskId;

    private String fileId;

    private String filePath;

    private String projectPath;

    private List<LlmQuestionInfo> questionInfos;

    private LlmConfig llmConfig;
}