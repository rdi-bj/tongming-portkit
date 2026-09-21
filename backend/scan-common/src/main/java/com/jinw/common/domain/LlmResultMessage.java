package com.jinw.common.domain;

import lombok.Data;

@Data
public class LlmResultMessage {

    private String taskId;

    private String fileId;

    private String projectPath;

    private String questionFilePath;

    private MigrationResult llmResponse;

    private RiscvAnalysisResult riscvAnalysisResult;

    private LlmQuestionInfo llmQuestionInfo;

    private String status;

    private String sessionId;

}