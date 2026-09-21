package com.jinw.worker.llm.config;

import lombok.Data;

@Data
public class TokenLimit {
    private Integer context;
    private Integer output;
}