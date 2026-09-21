package com.jinw.worker.llm.agent.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * 仅用于调试 / 日志
     */
    public Map<String, Object> toMap() {
        try {
            // 非 null 字段自动被 Jackson 过滤
            return OBJECT_MAPPER.convertValue(this, LinkedHashMap.class);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to convert request to map", e);
        }
    }

    /**
     * ✅ opencode / HTTP API 真正该用的方法
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + toJson();
    }
}
