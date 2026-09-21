package com.jinw.web.util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.domain.BTLlmConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public final class LlmConfigValidator {

    private static final String MODEL_UNAVAILABLE = "当前配置模型不可用，请检查服务";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private LlmConfigValidator() {
    }

    public static void validate(BTLlmConfig config) {
        if (config == null) {
            throw new BusinessException("请先在模型维护中，设置启用的模型");
        }

        String modelsUrl = buildModelsUrl(config.getLlmUrl());
        if (modelsUrl == null) {
            throw new BusinessException(MODEL_UNAVAILABLE);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("模型服务不可用, URL={}, statusCode={}", modelsUrl, response.statusCode());
                throw new BusinessException(MODEL_UNAVAILABLE);
            }

            if (!containsModel(response.body(), config.getLlmModel())) {
                log.warn("配置的模型 [{}] 不在可用模型列表中, URL={}", config.getLlmModel(), modelsUrl);
                throw new BusinessException(MODEL_UNAVAILABLE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("模型服务连接失败, URL={}", modelsUrl, e);
            throw new BusinessException(MODEL_UNAVAILABLE);
        }
    }

    /**
     * 判断可用模型列表中是否包含配置的模型名称。
     * 无法解析出模型列表时（非标准格式），保守返回 true，不阻断。
     */
    private static boolean containsModel(String body, String modelName) {
        if (modelName == null || modelName.isBlank() || body == null || body.isBlank()) {
            return true;
        }
        try {
            JSONObject root = JSONUtil.parseObj(body);
            JSONArray data = root.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return true;
            }
            for (Object item : data) {
                String id = null;
                if (item instanceof JSONObject modelObj) {
                    id = modelObj.getStr("id");
                } else if (item instanceof String s) {
                    id = s;
                }
                if (modelName.equals(id)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("解析模型列表失败, body={}", body, e);
            return true;
        }
    }

    private static String buildModelsUrl(String llmUrl) {
        if (llmUrl == null || llmUrl.isBlank()) {
            return null;
        }
        String url = llmUrl.strip();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url + "/models";
    }
}
