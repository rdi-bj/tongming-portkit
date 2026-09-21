package com.jinw.worker.llm.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderOptions {
    private String apiKey;
    private String baseURL;
}