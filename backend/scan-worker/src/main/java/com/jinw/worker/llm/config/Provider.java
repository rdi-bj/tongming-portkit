package com.jinw.worker.llm.config;

import lombok.Data;

import java.util.Map;

@Data
public class Provider {

    private String name;
    private String npm;

    private ProviderOptions options;

    /**
     * key = modelId
     * value = model 定义
     */
    private Map<String, ProviderModel> models;
}