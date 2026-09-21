package com.jinw.worker.llm.agent.api.response;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private T data;
}