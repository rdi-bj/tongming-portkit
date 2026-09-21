package com.jinw.worker.config;

import com.jinw.worker.llm.agent.api.HttpAPI;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class RetrofitTestConfig {

    public static HttpAPI createHttpAPI() {
        return createHttpAPI("http://127.0.0.1:9000");
    }

    public static HttpAPI createHttpAPI(String baseUrl) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // 可选级别
        logging.setLevel(HttpLoggingInterceptor.Level.NONE);
        String credentials = "opencode:Zjjw@123";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        Interceptor authInterceptor = chain -> {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("Authorization", "Basic " + encodedCredentials)
                    .build();
            return chain.proceed(request);
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        return retrofit.create(HttpAPI.class);
    }
}