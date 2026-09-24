package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jinw.web.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("b_t_llm_config")
@Schema(description = "大模型配置-DTO")
public class BTLlmConfig extends BaseEntity {

    @Schema(description = "大模型配置名称")
    private String configName;

    @Schema(description = "大模型URL")
    private String llmUrl;

    @Schema(description = "大模型名称")
    private String llmModel;

    @Schema(description = "大模型温度")
    private BigDecimal llmTemperature;

    @Schema(description = "大模型TopP")
    private BigDecimal llmTopP;

    @Schema(description = "大模型最大Tokens")
    private BigDecimal llmMaxTokens;

    @Schema(description = "大模型是否流式输出")
    private String llmStream;

    @Schema(description = "大模型是否开启思考")
    private String llmThink;

    @Schema(description = "是否启用")
    private String enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "大模型key")
    private String apiKey;

    @Schema(description = "上下文最大Token数")
    private Integer contextLimit;

    @Schema(description = "最大输出Token数")
    private Integer outputLimit;

    @Schema(description = "思考预算Token数")
    private Integer thinkingBudgetTokens;
}
