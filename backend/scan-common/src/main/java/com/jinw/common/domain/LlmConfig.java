package com.jinw.common.domain;

import lombok.Data;

@Data
public class LlmConfig{

    private String configName;

    private String llmUrl;

    private String llmModel;

    private String llmThink;

    private String apiKey;

    private Integer contextLimit;

    private Integer outputLimit;

    private Integer thinkingBudgetTokens;

}
