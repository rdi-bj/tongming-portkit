package com.jinw.worker.llm.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.domain.LlmConfig;
import com.jinw.common.domain.LlmQuestionInfo;
import com.jinw.common.domain.RiscvAnalysisResult;
import com.jinw.common.utils.ScanFileUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component("workerOpenCodeLLMClient")
public class OpenCodeLLMClient {

    @Value("${opencode.url:http://127.0.0.1:9000}")
    private String opencodeUrl;

    private HttpAPI httpAPI;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    public void init() {
        this.httpAPI = RetrofitTestConfig.createHttpAPI(opencodeUrl);
    }

    private final String providerId = "JinWang";

    /**
     * 构造 prompt
     */
//    public static String buildPrompt(String questionFilePath) {
//        String sourceCode = ScanFileUtil.readFileContent(questionFilePath);
//        return """
//        你是一名资深Linux内核、编译器和RISC-V移植专家。
//
//        当前正在进行软件从 x86、x86_64、ARM、ARM64 等架构迁移到 RISC-V 平台的适配工作。
//
//        请分析下面源码中所有可能影响 RISC-V 兼容性的内容，包括但不限于：
//
//        1. CPU架构宏判断
//        2. 汇编代码
//        3. SIMD指令
//        4. 寄存器访问
//        5. ptrace相关代码
//        6. syscall调用
//        7. 字长相关代码
//        8. ABI相关代码
//        9. 字节序处理
//        10. 原子操作
//        11. CPU特定优化
//        12. 条件编译分支
//        13. 内核接口差异
//        14. 第三方库依赖中的架构绑定逻辑
//
//        对于发现的问题，请给出具体修改建议。
//
//        =========================
//        输出要求
//        =========================
//
//        必须严格输出JSON。
//
//        禁止输出：
//        1. 只返回JSON对象
//        2. 不允许使用Markdown
//        3. 不允许出现 ```json
//        4. 不允许输出解释文字
//        5. 返回内容必须可以直接被 Jackson ObjectMapper.readValue() 解析
//
//        返回格式如下：
//
//        {
//          "fileType": "c",
//          "riskLevel": "HIGH",
//          "summary": "发现3处需要适配RISC-V的代码",
//          "changes": [
//            {
//              "oldLine": "27-35",
//              "newLine": "27-41",
//              "type": "MODIFY",
//              "oldCode": "原代码",
//              "newCode": "修改后代码",
//              "description": "新增RISC-V架构识别"
//            }
//          ]
//        }
//
//        字段说明：
//
//        riskLevel取值：
//
//        - LOW
//        - MEDIUM
//        - HIGH
//
//        type取值：
//
//        - ADD
//        - MODIFY
//        - DELETE
//
//        如果无需修改：
//
//        {
//          "fileType": "c",
//          "riskLevel": "LOW",
//          "summary": "未发现RISC-V兼容性问题",
//          "changes": []
//        }
//
//        =========================
//        待分析源码
//        =========================
//
//        %s
//        """.formatted(sourceCode);
//    }
    public static String buildPrompt(String questionFilePath) {
        String sourceCode = ScanFileUtil.readFileContent(questionFilePath);
        return """
                你是一名资深Linux内核、编译器和RISC-V移植专家。
                
                当前正在进行软件从 x86、x86_64、ARM、ARM64 等架构迁移到 RISC-V 平台的适配工作。
                
                请分析下面源码中所有可能影响 RISC-V 兼容性的内容，包括但不限于：
                
                1. CPU架构宏判断
                2. 汇编代码
                3. SIMD指令
                4. 寄存器访问
                5. ptrace相关代码
                6. syscall调用
                7. 字长相关代码
                8. ABI相关代码
                9. 字节序处理
                10. 原子操作
                11. CPU特定优化
                12. 条件编译分支
                13. 内核接口差异
                14. 第三方库依赖中的架构绑定逻辑
                
                对于发现的问题，请给出具体修改建议。
                
                =========================
                输出要求
                =========================
                
                直接输出修改后的代码,只需要返回代码文本，禁止写入文件。
                
                禁止输出：    
                1. 不允许使用Markdown
                2. 不允许出现 ```json
                3. 不允许输出解释文本
                
                =========================
                待分析源码
                =========================
                
                %s
                """.formatted(sourceCode);
    }

    public static String buildQuestionPrompt(LlmQuestionInfo llmQuestionInfo) {
        return """
            你是一名资深Linux内核与RISC-V移植专家。
            
            请仅针对以下代码中**最关键的RISC-V兼容性问题**给出一条修改建议。
            
            要求：
            1. 不输出示例代码、不重构代码
            2. 不使用Markdown、不输出JSON
            3. 不解释原因，不补充说明
            4. 给出对应修改的方法并整理成一段语义连续的文本
            
            源码如下：
            %s
            """.formatted(llmQuestionInfo.getText());
    }

    public static String buildQuestionPrompt(LlmQuestionInfo llmQuestionInfo,String filePath) {
        String sourceCode = ScanFileUtil.readFileContent(filePath);
        return String.format(
                """
                你是一名资深Linux内核与RISC-V移植专家。
                
                以下是源代码文件的内容：
                %s
                
                以下是疑似存在RISC-V兼容性问题的代码片段：
                %s
                
                问题描述：%s
                
                代码行范围：第%d行到第%d行
                
                请你判断上述代码片段是否确实存在RISC-V兼容性问题。
                
                只需回答"是"或"否"，不要输出任何其他内容。
                """,
                sourceCode,
                llmQuestionInfo.getText(),
                llmQuestionInfo.getDescription(),
                llmQuestionInfo.getStartLine(),
                llmQuestionInfo.getEndLine()
        );
    }

    public String buildAnalyzePrompt(String questionFilePath) {
        return """
        你是一名资深Linux内核、编译器和RISC-V移植专家。
        
        当前正在进行软件从 x86、x86_64、ARM、ARM64 等架构迁移到 RISC-V 平台的适配工作。
        
        我将给你一个源码文件的绝对路径，请你先读取该文件内容，然后分析其中所有可能影响 RISC-V 兼容性的内容，并给出适配方案判定。
        
        =========================
        源码文件路径
        =========================
        %s
        
        =========================
        建议输出路径规则（非常重要）
        =========================
        
        你需要基于源码文件路径生成一个"建议的RISC-V文件路径"，规则如下：
        
        1. 源码路径中必然包含一个名为 "unzip" 的目录
        2. 将该目录名替换为 "adapt"，其余路径保持不变
        3. 示例：
           源码路径：/xxx/xxx/uuid/unzip/proot-5.4.0/src/loader/assembly-arm.h
           建议路径：/xxx/xxx/uuid/adapt/proot-5.4.0/src/loader/assembly-arm.h
        
        4. 文件名调整规则（根据方案类型）：
           - 方案A（独立架构实现）：在原文件名基础上加入 risc/riscv/rv64 等关键字
           - 方案B（方案级适配）：文件名保持不变，后缀添加 .risc
           - 方案C（代码改动适配）：文件名保持不变
        
        =========================
        需要检查的兼容性点
        =========================
        1. CPU架构宏判断
        2. 汇编代码
        3. SIMD指令
        4. 寄存器访问
        5. ptrace相关代码
        6. syscall调用
        7. 字长相关代码
        8. ABI相关代码
        9. 字节序处理
        10. 原子操作
        11. CPU特定优化
        12. 条件编译分支
        13. 内核接口差异
        14. 第三方库依赖中的架构绑定逻辑
        
        =========================
        适配方案判定规则
        =========================
        
        你需要从以下三种方案中选择一种：
        
        【方案A：独立架构实现（同构扩展）】
        适用条件：原项目已为 x86、ARM 等架构分别维护独立实现文件
        判定标准：存在 arch/ 或 platform/ 等目录下按架构分离的文件
        
        【方案B：无需代码改动的方案级适配】
        适用条件：代码中不包含在 RISC-V 架构下无法运行的内容（无 RISC-V 不支持的汇编/SIMD/寄存器访问/架构宏等），无需修改源码即可在 RISC-V 上编译运行
        判定标准：代码中不包含 RISC-V 架构下无法运行的代码，无需修改
        
        【方案C：需要代码改动的适配】
        适用条件：必须通过修改代码才能支持 RISC-V
        判定标准：存在 RISC-V 不支持的汇编/SIMD/架构宏等
        
        =========================
        输出格式（严格JSON，不要有任何其他输出）
        =========================
        
        {
          "selected_scheme": "",
          "reasoning": "",
          "original_file_path": "%s",
          "suggested_riscv_file_path": "",
          "modification_summary": ""
        }
        
        字段说明：
        - selected_scheme：只能是 A、B、C 之一
        - reasoning：选择该方案的详细理由
        - original_file_path：源码文件绝对路径（与上方"源码文件路径"一致）
        - suggested_riscv_file_path：按上方"建议输出路径规则"生成的路径
        - modification_summary：需要修改时简要说明修改点；方案B无需修改时填"无需修改"
        
        """.formatted(questionFilePath, questionFilePath);
    }

    public String buildSchemeAPrompt(RiscvAnalysisResult analysis) {
        return """
        你是一名资深Linux内核、编译器和RISC-V移植专家。
        
        根据以下分析结果，为方案A（独立架构实现）生成 RISC-V 适配代码。
        
        =========================
        分析结果
        =========================
        原文件路径：%s
        建议RISC-V文件路径：%s
        修改要点：%s
        
        =========================
        任务要求
        =========================
        
        1. 请先读取原文件路径获取源码内容
        2. 将源码适配为 RISC-V 架构的实现
        3. 将适配后的代码写入建议的 RISC-V 文件路径
        4. 禁止在原文件中修改任何内容
        
        =========================
        输出要求（严格JSON，不要有任何其他输出）
        =========================
        
        完成写入后，仅输出以下 JSON，不要输出代码内容：
        
        {
          "written_file_path": "实际写入的文件路径",
          "status": "success|failed",
          "message": "成功或失败的说明"
        }
        
        """.formatted(
                analysis.getOriginalFilePath(),
                analysis.getSuggestedRiscvFilePath(),
                analysis.getModificationSummary()
        );
    }

    public String buildSchemeBPrompt(RiscvAnalysisResult analysis) {
        return """
        你是一名资深Linux内核、编译器和RISC-V移植专家。
        
        根据以下分析结果，为方案B（无需代码改动的方案级适配）生成适配方案文档。
        
        =========================
        分析结果
        =========================
        原文件路径：%s
        建议RISC-V文件路径：%s
        修改要点：%s
        
        =========================
        任务要求
        =========================
        
        1. 请先读取原文件路径获取源码内容（用于理解上下文）
        2. 生成 .risc 方案文档，内容必须包含以下章节：
           【依赖条件】
           【编译选项】
           【验证方法】
           【预期结果】
        3. 将生成的方案文档写入建议的 RISC-V 文件路径（.risc 文件），不要写入任何代码
        4. 禁止在原文件中修改任何内容

        =========================
        输出要求（严格JSON，不要有任何其他输出）
        =========================
        
        完成写入后，仅输出以下 JSON，不要输出文档内容：
        
        {
          "written_file_path": "实际写入的文件路径",
          "status": "success|failed",
          "message": "成功或失败的说明"
        }
        
        """.formatted(
                analysis.getOriginalFilePath(),
                analysis.getSuggestedRiscvFilePath(),
                analysis.getModificationSummary()
        );
    }

    public String buildSchemeCPrompt(RiscvAnalysisResult analysis) {
        return """
        你是一名资深Linux内核、编译器和RISC-V移植专家。
        
        根据以下分析结果，为方案C（需要代码改动的适配）生成修改后的源码。
        
        =========================
        分析结果
        =========================
        原文件路径：%s
        建议RISC-V文件路径：%s
        修改要点：%s
        
        =========================
        任务要求
        =========================
        
        1. 请先读取原文件路径获取源码内容
        2. 对源码进行 RISC-V 适配修改
        3. 将修改后的源码写入建议的 RISC-V 文件路径
        4. 确保不影响 x86/ARM 行为
        5. 禁止在原文件中修改任何内容
        
        =========================
        输出要求（严格JSON，不要有任何其他输出）
        =========================
        
        完成写入后，仅输出以下 JSON，不要输出代码内容：
        
        {
          "written_file_path": "实际写入的文件路径",
          "status": "success|failed",
          "message": "成功或失败的说明"
        }
        
        """.formatted(
                analysis.getOriginalFilePath(),
                analysis.getSuggestedRiscvFilePath(),
                analysis.getModificationSummary()
        );
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

    public String sendQuestionPrompt(
            String sessionId,
            LlmQuestionInfo llmQuestionInfo
    ) throws Exception {
        String prompt =
                buildQuestionPrompt(llmQuestionInfo);
        long requestTime = System.currentTimeMillis();
        sendPromptAsync(
                sessionId,
                prompt
        );
        MessageWrapper assistant =
                waitAssistantMessage(
                        sessionId,
                        requestTime,
                        300
                );
        return getMessageText(
                assistant
        );
    }

    public String sendQuestionPrompt(
            String sessionId,
            LlmQuestionInfo llmQuestionInfo,
            String filePath
    ) throws Exception {
        String prompt =
                buildQuestionPrompt(llmQuestionInfo,filePath);
        long requestTime = System.currentTimeMillis();
        sendPromptAsync(
                sessionId,
                prompt
        );
        MessageWrapper assistant =
                waitAssistantMessage(
                        sessionId,
                        requestTime,
                        300
                );
        return getMessageText(
                assistant
        );
    }

    public String getMessageText(
            MessageWrapper message
    ) {
        if (message == null
                || message.getParts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        for (com.jinw.worker.llm.agent.api.dto.part.Part part : message.getParts()) {

            if ("text".equals(part.getType())) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
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
            return OBJECT_MAPPER.readValue(json, clazz);
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

    public MessageWrapper waitAssistantMessage(
            String sessionId,
            long requestStartTime,
            int maxWaitSeconds
    ) throws Exception {

        long deadline =
                System.currentTimeMillis()
                        + maxWaitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Response<List<MessageWrapper>> response =
                    httpAPI.listMessage(
                            sessionId,
                            Collections.emptyMap()
                    ).execute();
            if (response.isSuccessful()) {
                log.info("正在轮询{}的message", sessionId);
                List<MessageWrapper> messages =
                        response.body();
                if (messages != null && !messages.isEmpty()) {
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        MessageWrapper msg =
                                messages.get(i);
                        if (isCompletedAssistant(msg)
                                && msg.getInfo().getTime().getCompleted() >= requestStartTime) {
                            return msg;
                        }
                    }
                }
            }
            Thread.sleep(10000);
        }
        throw new RuntimeException(
                "等待模型回复超时"
        );
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

    /**
     * 中止指定 session，释放 AI 适配线程
     */
    public void abortSession(String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Response<Void> response = httpAPI.abortSession(sessionId).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "Abort session failed: " + response.errorBody().string()
            );
        }
        log.info("已中止 opencode session: {}", sessionId);
    }
}