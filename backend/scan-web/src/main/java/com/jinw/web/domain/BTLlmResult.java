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
@TableName("b_t_llm_result")
@Schema(description = "大模型结果-DTO")
public class BTLlmResult extends BaseEntity {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "旧行")
    private String oldLine;

    @Schema(description = "新行")
    private String newLine;

    @Schema(description = "类别")
    private String type;

    @Schema(description = "旧代码")
    private String oldCode;

    @Schema(description = "新代码")
    private String newCode;

    @Schema(description = "修改描述")
    private String description;
}
