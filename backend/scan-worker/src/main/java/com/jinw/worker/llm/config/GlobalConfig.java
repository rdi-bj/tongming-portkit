package com.jinw.worker.llm.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalConfig {
    private Map<String, Provider> provider;
}