package com.jinw.web.domain;

import com.jinw.web.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class LlmQuestionResult {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "已适配问题数")
    private int adaptQuestionCount;

    @Schema(description = "总问题数")
    private int totalQuestionCount;

    @Schema(description = "AI适配进度")
    private String status;

    @Schema(description = "AI适配结果")
    private String adaptResult;

    @Schema(description = "问题AI适配结果")
    private List<BTQuestionInfo> btQuestionInfos;

    @Schema(description = "AI适配内容")
    private String adaptText;

}
