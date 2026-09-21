package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jinw.web.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("b_t_question_info")
public class BTQuestionInfo extends BaseEntity {

    @Schema(description = "文本")
    private String text;

    @Schema(description = "开始行")
    private int startLine;

    @Schema(description = "结束行")
    private int endLine;

    @Schema(description = "开始列")
    private int startCol;

    @Schema(description = "结束列")
    private int endCol;

    @Schema(description = "问题类型")
    private String questionType;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "规则ID")
    private String ruleId;

    @Schema(description = "规则描述")
    private String description;

    @Schema(description = "AI适配进度")
    private String status;

    @Schema(description = "AI适配结果")
    private String adaptResult;

    @Schema(description = "文件ID")
    private String fileId;

    @Schema(description = "AI判断结果 0-不是问题 1-是问题")
    private String aiVerifyStatus;

    @Schema(description = "是否不支持riscv")
    private String hitKnowledge;

    @Schema(description = "架构")
    private String framework;

    @Schema(description = "是否需要AI适配")
    private String haveAdapt;
}
