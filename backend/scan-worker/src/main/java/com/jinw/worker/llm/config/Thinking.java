package com.jinw.worker.llm.config;

import lombok.Data;

@Data
public class Thinking {
    private String type;
    private Integer budgetTokens;
}