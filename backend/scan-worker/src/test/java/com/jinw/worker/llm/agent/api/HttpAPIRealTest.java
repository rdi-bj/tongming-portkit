package com.jinw.worker.llm.agent.api;

import com.jinw.common.domain.LlmConfig;
import com.jinw.worker.config.RetrofitTestConfig;
import com.jinw.worker.llm.agent.api.dto.*;
import com.jinw.worker.llm.agent.api.request.*;
import com.jinw.worker.llm.agent.api.response.ApiResponse;
import com.jinw.worker.llm.agent.api.response.PromptResponse;
import com.jinw.worker.llm.config.GlobalConfig;
import com.jinw.worker.llm.config.Provider;
import com.jinw.worker.llm.utils.OpenCodeLLMClient;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAPIRealTest {

    private final HttpAPI httpAPI = RetrofitTestConfig.createHttpAPI();

    @Test
    void testGetGlobalConfig() throws IOException {
        retrofit2.Response<GlobalConfig> response =
                httpAPI.getGlobalConfig().execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            GlobalConfig body = response.body();
            Map<String, Provider> provider = body.getProvider();
            Provider test = provider.get("newapi");
            System.out.println(test);
            System.out.println("======================");
            LlmConfig llmConfig = new LlmConfig();
            llmConfig.setApiKey("sk-UUQDK5sXDFSDFTb3j835yi2QKQlp11vqGHb0HQGgc9rNNESe");
            llmConfig.setLlmUrl("http://192.168.2.245:3000/v1");
            llmConfig.setConfigName("newapi");
            llmConfig.setContextLimit(131072);
            llmConfig.setLlmModel("deepseek-reasoner");
            llmConfig.setLlmThink("1");
            llmConfig.setOutputLimit(65536);
            llmConfig.setThinkingBudgetTokens(8192);
            OpenCodeLLMClient openCodeLLMClient = new OpenCodeLLMClient();
            System.out.println(openCodeLLMClient.buildProvider(llmConfig));
            Boolean b = openCodeLLMClient.judgeHaveLlmConfig(llmConfig);
            System.out.println(b);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }

    @Test
    void testUpdateGlobalConfig() throws IOException {
        LlmConfig llmConfig = new LlmConfig();
        llmConfig.setApiKey("sk-UUQDK5sXDFSDFTb3j835yi2QKQlp11vqGHb0HQGgc9rNNESe");
        llmConfig.setLlmUrl("http://192.168.2.245:3000/v1");
        llmConfig.setConfigName("newapi");
        llmConfig.setContextLimit(131072);
        llmConfig.setLlmModel("deepseek-reasoner");
        llmConfig.setLlmThink("1");
        llmConfig.setOutputLimit(65536);
        llmConfig.setThinkingBudgetTokens(8192);
        OpenCodeLLMClient openCodeLLMClient = new OpenCodeLLMClient();
        Provider provider = openCodeLLMClient.buildProvider(llmConfig);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setProvider(Map.of("JinWang",provider));
        Response<GlobalConfig> response = httpAPI.updateGlobalConfig(globalConfig).execute();
        if (response.isSuccessful()) {
            GlobalConfig body = response.body();
            System.out.println(body.getProvider().get("JinWang"));
        }else{
            System.out.println("Error body: " + response.errorBody().string());
        }
    }


    /**
     * ✅ 正常调用接口
     */
    @Test
    void testListSessions_realCall() throws IOException {
        SessionQueryRequest request = new SessionQueryRequest();
//        request.setDirectory();

        retrofit2.Response<List<Session>> response =
                httpAPI.listSessions(request.toMap()).execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            List<Session> sessions = response.body();
            for (Session session : sessions) {
                System.out.println(session);
            }
            System.out.println("Session count: " + (sessions == null ? 0 : sessions.size()));

            assertNotNull(sessions);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }


    @Test
    void createSession() throws IOException {
        Model model = new Model();
        model.setProviderID("deepseek");
        model.setId("deepseek-v4-pro");

        CreateSessionBody body = new CreateSessionBody();
        String uuid = UUID.randomUUID().toString();

        System.out.println("生成的UUID: " + uuid);

        body.setTitle(uuid);
        // openAI配置文件
        body.setModel(model);
        body.setDirectory("/Users");

        retrofit2.Response<Session> response =
                httpAPI.creatSession("/Users/shenyanghan",body).execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            Session session = response.body();

            System.out.println(session);
            System.out.println(session.getId());
            // ses_15969ae48ffeHaR4kLx6CH5WJP
            assertNotNull(session);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }

    @Test
    void promptAsync() throws IOException {
        String sessionId = "ses_131a6c2e6ffeYAcMfh0nH5B1Sb";
        System.out.println("sessionId: " + sessionId);

        PromptAsyncBody body = new PromptAsyncBody();

        List<Part> parts = new ArrayList<>();
        body.setParts(parts);
        Part part = new Part();
        parts.add(part);
        part.setText("帮我在当前目录下生成一个txt文件，内容为123");

        retrofit2.Response<Void> response =
                httpAPI.promptAsync(sessionId, body).execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            assertNotNull(response);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }

    @Test
    void prompt() throws IOException {
        String sessionId = "ses_154faf7cbffeV5yW9FZtDBF2BZ";
        System.out.println("sessionId: " + sessionId);

        PromptBody body = new PromptBody();
        Prompt prompt = new Prompt();
        prompt.setText("你好");
        body.setPrompt(prompt);

        retrofit2.Response<ApiResponse<PromptResponse>> response =
                httpAPI.prompt(sessionId, body).execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            PromptResponse promptResponse = response.body().getData();

            System.out.println(promptResponse);
            System.out.println(promptResponse.getId());
            // ses_15969ae48ffeHaR4kLx6CH5WJP
            assertNotNull(promptResponse);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }

    @Test
    void listMessages() throws IOException {
        String sessionId = "ses_135b6ca6effeJziHZc5Ei5tjPl";
        ListMessageRequest request = new ListMessageRequest();
//        request.setDirectory();

        retrofit2.Response<List<MessageWrapper>> response =
                httpAPI.listMessage(sessionId, request.toMap()).execute();

        // 打印状态码
        System.out.println("HTTP Status: " + response.code());

        if (response.isSuccessful()) {
            List<MessageWrapper> messages = response.body();
            for (MessageWrapper message : messages) {

            }
            System.out.println("messages count: " + (messages == null ? 0 : messages.size()));

//            sessionId: ses_1593c5842ffedQVUS5DK1Sd04D
//            messageID: 27c379dc-557b-4c6e-9f12-f60ac109cc92
            assertNotNull(messages);
        } else {
            System.out.println("Error body: " + response.errorBody().string());
        }

        // 断言：至少服务是可用的
        assertTrue(response.code() > 0);
    }
}