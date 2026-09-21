package com.jinw.worker.llm.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Model {

    /*{
        "$schema": "https://opencode.ai/config.json",
            "provider": {
        "anthropic": {
            "npm": "@ai-sdk/anthropic",
                    "name": "Anthropic",
                    "options": {
                "apiKey": "sk-ant-xxxx"
            },
            "models": {
                "claude-3-5-sonnet-20241022": {
                    "name": "Claude 3.5 Sonnet"
                }
            }
        },

        "openai": {
            "options": {
                "baseURL": "https://api.openai.com/v1",
                        "apiKey": "sk-xxxx"
            },
            "models": {
                "gpt-4o": {},
                "gpt-4o-mini": {}
            }
        },

        "myllm": {
            "npm": "@ai-sdk/openai-compatible",
                    "name": "My Custom LLM",
                    "options": {
                "baseURL": "http://localhost:8000/v1",
                        "apiKey": "your-token"
            },
            "models": {
                "qwen2-72b": {},
                "deepseek-coder": {}
            }
        }
    },

        "model": "openai:gpt-4o"
    }*/
    // 模型id
    private String id;
    // 供应商
    private String providerID;
    private String variant;
    // 响应时存在
    private String modelID;
}