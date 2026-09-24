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
@TableName("b_t_question_file_info")
public class BTQuestionFileInfo extends BaseEntity {

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

    @Schema(description = "AI判断结果 0-不通过 1-通过")
    private String aiVerifyStatus;

    @Schema(description = "是否不支持riscv")
    private String hitKnowledge;

    @Schema(description = "是否需要AI适配")
    private String haveAdapt;
}
