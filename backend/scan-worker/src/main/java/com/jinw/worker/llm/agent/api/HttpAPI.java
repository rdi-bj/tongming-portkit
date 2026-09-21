package com.jinw.worker.llm.agent.api;

import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import com.jinw.worker.llm.agent.api.dto.MessageWrapper;
import com.jinw.worker.llm.agent.api.dto.Session;
import com.jinw.worker.llm.agent.api.request.CreateSessionBody;
import com.jinw.worker.llm.agent.api.request.PromptAsyncBody;
import com.jinw.worker.llm.agent.api.request.PromptBody;
import com.jinw.worker.llm.agent.api.response.ApiResponse;
import com.jinw.worker.llm.agent.api.response.PromptResponse;
import com.jinw.worker.llm.config.GlobalConfig;
import com.jinw.worker.llm.config.Provider;
import org.springframework.stereotype.Service;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

@RetrofitClient(baseUrl = "http://127.0.0.1:9000")
@Service
public interface HttpAPI {
    @GET("session")
    retrofit2.Call<List<Session>> listSessions(
            @QueryMap Map<String, Object> query
    );

    @POST("session")
    retrofit2.Call<Session> creatSession(
            @Header("x-opencode-directory") String directory,
            @Body CreateSessionBody body
    );

    @POST("session/{sessionId}/prompt_async")
    retrofit2.Call<Void> promptAsync(
            @Path("sessionId") String sessionId,
            @Body PromptAsyncBody body
    );

    @POST("session/{sessionId}/abort")
    retrofit2.Call<Void> abortSession(
            @Path("sessionId") String sessionId
    );

    @POST("api/session/{sessionId}/prompt")
    retrofit2.Call<ApiResponse<PromptResponse>> prompt(
            @Path("sessionId") String sessionId,
            @Body PromptBody body
    );

    @GET("session/{sessionId}/message")
    retrofit2.Call<List<MessageWrapper>> listMessage(
            @Path("sessionId") String sessionId,
            @QueryMap Map<String, Object> query
    );

    @GET("global/config")
    retrofit2.Call<GlobalConfig> getGlobalConfig(
    );

    @PATCH("global/config")
    retrofit2.Call<GlobalConfig> updateGlobalConfig(
            @Body GlobalConfig globalConfig
    );
}
