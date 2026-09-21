package com.jinw.worker.llm.config;

import lombok.Data;

@Data
public class ProviderModel {

    private String name;

    private TokenLimit limit;

    private Modalities modalities;

    private Options options;
}