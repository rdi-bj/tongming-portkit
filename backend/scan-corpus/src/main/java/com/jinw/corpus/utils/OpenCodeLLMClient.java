package com.jinw.corpus.utils;

import cn.hutool.json.JSONUtil;
import com.jinw.common.domain.LlmConfig;
import com.jinw.worker.config.RetrofitTestConfig;
import com.jinw.worker.llm.agent.api.HttpAPI;
import com.jinw.worker.llm.agent.api.dto.MessageWrapper;
import com.jinw.worker.llm.agent.api.dto.Model;
import com.jinw.worker.llm.agent.api.dto.Part;
import com.jinw.worker.llm.agent.api.dto.Session;
import com.jinw.worker.llm.agent.api.request.CreateSessionBody;
import com.jinw.worker.llm.agent.api.request.PromptAsyncBody;
import com.jinw.worker.llm.agent.api.request.SessionQueryRequest;
import com.jinw.worker.llm.config.*;
import com.jinw.worker.llm.utils.ProviderCompareUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component("corpusOpenCodeLLMClient")
public class OpenCodeLLMClient {

    @Value("${opencode.url:http://127.0.0.1:9000}")
    private String opencodeUrl;

    private HttpAPI httpAPI;

    @PostConstruct
    public void init() {
        this.httpAPI = RetrofitTestConfig.createHttpAPI(opencodeUrl);
    }

    private final String providerId = "JinWang";

    public String buildCorpusPrompt(
            String absolutePath,
            int startLine,
            int endLine
    ) {
        return """
        你是一名资深 Linux 内核、编译器与 RISC-V 移植专家。
        
        当前正在进行软件从 x86 / x86_64 / ARM / ARM64 等架构向 RISC-V 架构迁移的适配工作。
        
        我将向你提供一个源码文件的绝对路径、问题起始行号和结束行号。
        请你完成以下任务：
        
        1. 读取并分析该行号范围内的代码；
        2. 根据需要，向上/向下扩展阅读相关上下文（函数、宏定义、结构体、调用关系等）；
        3. 判断该代码片段是否存在影响 RISC-V 兼容性的问题，例如：
           - CPU 架构宏判断
           - 汇编代码
           - SIMD 指令
           - 寄存器访问
           - ptrace / syscall
           - 字长、ABI、字节序
           - 原子操作
           - 架构相关优化
           - 条件编译
           - 内核接口差异
           - 第三方库架构绑定
        4. 如果存在兼容性问题，给出**完整、可编译、可直接替换**的 RISC-V 适配后代码；
        5. 如果不存在问题，请基于最佳实践给出合理的 RISC-V 风格改写；
        6. 生成一条语料 JSON 对象，字段必须严格如下：
        
        {
          "type": "source_code",
          "language": "代码语言，如：c / cpp / asm / rust",
          "sourceChip": "源架构，如：x86_64 / arm64",
          "targetChip": "risc-v",
          "sourceCode": "原始代码（含必要上下文，不要截断）",
          "targetCode": "适配后的 RISC-V 代码",
          "description": "问题原因简述 + 适配思路，不超过 200 字"
        }
        
        ===== 严格要求 =====
        
        1. 输出必须是 **合法 JSON**，仅此一条；
        2. 不要使用 Markdown；
        3. 不要使用 ```json ``` 包裹；
        4. 不要输出任何解释、注释、前缀、后缀；
        5. 不要输出多个 JSON；
        6. sourceCode 和 targetCode 中，换行必须使用 \\n 转义；
        7. 确保 JSON 可被程序直接解析。
        
        ===== 输入信息 =====
        
        源码绝对路径：%s
        问题起始行号：%d
        问题结束行号：%d
        """.formatted(absolutePath, startLine, endLine);
    }


    public Boolean judgeHaveLlmConfig(LlmConfig llmConfig) throws IOException {
        Response<GlobalConfig> response = httpAPI.getGlobalConfig().execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "查询全局配置出错: " + response.errorBody().string()
            );
        }

        Map<String, Provider> providerMap = response.body().getProvider();
        if (providerMap == null){
            return false;
        }

        Provider provider = providerMap.get(providerId);
        if (provider == null){
            return false;
        }

        Provider newProvider = buildProvider(llmConfig);

        return ProviderCompareUtil.isSameProvider(provider, newProvider);
    }

    public void updateLlmConfig(LlmConfig llmConfig) throws IOException {
        Provider provider = buildProvider(llmConfig);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setProvider(Map.of(providerId,provider));
        Response<GlobalConfig> response = httpAPI.updateGlobalConfig(globalConfig).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "更新全局配置出错: " + response.errorBody().string()
            );
        }
    }

    public Provider buildProvider(LlmConfig cfg) {

        Provider provider = new Provider();
        provider.setName(providerId);
        provider.setNpm("@ai-sdk/openai-compatible");

        // options
        ProviderOptions providerOptions = new ProviderOptions();
        providerOptions.setApiKey(cfg.getApiKey() == null ? "" : cfg.getApiKey());
        providerOptions.setBaseURL(cfg.getLlmUrl());
        provider.setOptions(providerOptions);

        // model
        ProviderModel model = new ProviderModel();
        model.setName(cfg.getLlmModel());

        // limit
        TokenLimit limit = new TokenLimit();
        limit.setContext(cfg.getContextLimit());
        limit.setOutput(cfg.getOutputLimit());
        model.setLimit(limit);

        // modalities（固定）
        Modalities modalities = new Modalities();
        modalities.setInput(List.of("text"));
        modalities.setOutput(List.of("text"));
        model.setModalities(modalities);

        // thinking
        if ("1".equals(cfg.getLlmThink())) {
            Thinking thinking = new Thinking();
            thinking.setType("enabled");
            thinking.setBudgetTokens(cfg.getThinkingBudgetTokens());

            Options options = new Options();
            options.setThinking(thinking);
            model.setOptions(options);
        }

        // models map
        Map<String, ProviderModel> models = new HashMap<>();
        models.put(cfg.getLlmModel(), model);
        provider.setModels(models);

        return provider;
    }

    /**
     * 获取或创建 session
     */
    public String getOrCreateSession(String sessionId, String taskId, String projectPath, LlmConfig llmConfig)
            throws IOException {

        // 校验opencode里是否包含这个sessionId或者title为taskId的session
        SessionQueryRequest request = new SessionQueryRequest();
        Response<List<Session>> response = httpAPI.listSessions(request.toMap()).execute();
        List<Session> sessions = response.body();
        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "Create session failed: " + response.errorBody().string()
            );
        }
        for (Session session : sessions) {
            // 这里还需要校验一下session的model是不是现在激活的
            String modelId = session.getModel().getId();
            String providerID = session.getModel().getProviderID();
            //这里添加title比较主要是为了批量适配的时候不会及时返回sessionId给主机端
            if ((session.getId().equals(sessionId) || taskId.equals(session.getTitle()))
                    && llmConfig.getLlmModel().equals(modelId)
                    && providerId.equals(providerID)){
                log.info("{}存在且模型调用为现有激活的模型，延用原有session",sessionId != null ? sessionId : session.getId());
                return sessionId != null ? sessionId : session.getId();
            }
        }

        Model model = new Model();
        model.setProviderID(providerId);
        model.setId(llmConfig.getLlmModel());

        CreateSessionBody body = new CreateSessionBody();
        body.setTitle(taskId);
        body.setModel(model);
        body.setDirectory(projectPath);
        body.setWorkspace(projectPath);

        Response<Session> createSessionResponse =
                httpAPI.creatSession(projectPath,body).execute();

        if (!createSessionResponse.isSuccessful()) {
            throw new RuntimeException(
                    "Create session failed: " + createSessionResponse.errorBody().string()
            );
        }

        sessionId = createSessionResponse.body().getId();
        log.info("创建新session，sessionId为{}，工作目录为{}",sessionId,projectPath);
        return sessionId;
    }

    public <T> T sendFilePrompt(
            String sessionId,
            String prompt,
            Class<T> resultType
    ) throws Exception {
        long requestTime = System.currentTimeMillis();
        sendPromptAsync(
                sessionId,
                prompt
        );
        return waitAssistantMessage(
                        sessionId,
                        requestTime,
                        300,
                        resultType
                );
    }

    public void sendPromptAsync(
            String sessionId,
            String promptText
    ) throws IOException {

        PromptAsyncBody body = new PromptAsyncBody();
        List<Part> parts = new ArrayList<>();
        body.setParts(parts);
        Part part = new Part();
        part.setType("text");
        part.setText(promptText);
        parts.add(part);

        Response<Void> response =
                httpAPI.promptAsync(sessionId, body).execute();

        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "PromptAsync failed: "
                            + response.errorBody().string()
            );
        }
    }

    public <T> T waitAssistantMessage(
            String sessionId,
            long requestStartTime,
            int maxWaitSeconds,
            Class<T> resultType
    ) throws Exception {

        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            Response<List<MessageWrapper>> response =
                    httpAPI.listMessage(sessionId, Collections.emptyMap()).execute();

            if (response.isSuccessful() && response.body() != null) {
                log.info("轮询 assistant message，sessionId={}", sessionId);

                List<MessageWrapper> messages = response.body();

                // 倒序：优先最新
                for (int i = messages.size() - 1; i >= 0; i--) {
                    MessageWrapper msg = messages.get(i);

                    if (!isCompletedAssistant(msg, requestStartTime)) {
                        continue;
                    }

                    String content = extractContent(msg);
                    T result = tryParseJson(content, resultType);
                    if (result != null) {
                        log.info("成功解析 assistant JSON，类型={}", resultType.getSimpleName());
                        return result;
                    }
                }
            }

            Thread.sleep(10_000);
        }

        throw new RuntimeException(
                "等待模型回复超时，sessionId=" + sessionId
        );
    }

    private boolean isCompletedAssistant(MessageWrapper msg, long requestStartTime) {
        if (msg == null || msg.getInfo() == null) {
            return false;
        }

        if (!"assistant".equals(msg.getInfo().getRole())) {
            return false;
        }

        Long completed = msg.getInfo().getTime().getCompleted();
        return completed != null && completed >= requestStartTime;
    }

    private String extractContent(MessageWrapper msg) {
        if (msg == null || msg.getParts() == null || msg.getParts().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (com.jinw.worker.llm.agent.api.dto.part.Part part : msg.getParts()) {
            if (part == null) {
                continue;
            }

            if ("text".equals(part.getType())) {
                String text = part.getText();
                if (text != null && !text.isEmpty()) {
                    sb.append(text);
                }
            }
        }

        return sb.toString();
    }

    private <T> T tryParseJson(String content, Class<T> clazz) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String json = extractJsonBlock(content);
        if (json == null) {
            return null;
        }

        try {
            return JSONUtil.toBean(json, clazz, true);
        } catch (Exception e) {
            log.debug("JSON 解析失败，类型={}，错误={}",
                    clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String extractJsonBlock(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private boolean isCompletedAssistant(
            MessageWrapper msg
    ) {
        return "assistant".equals(
                msg.getInfo().getRole()
        )
                &&
                msg.getInfo()
                        .getTime()
                        .getCompleted() != null;
    }

    public String convertTaskIdToTitle(String prefix, String taskId){
        return prefix + "_" + taskId;
    }
}