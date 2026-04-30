package com.vemo.codereview.llm.client;

import com.alibaba.fastjson.JSONObject;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenAiCompatibleClient implements ChatModelClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request, LlmRuntimeConfig runtimeConfig) {
        try {
            log.debug("request={}", JSONObject.toJSONString(request));
            OkHttpClient okHttpClient = buildClient(runtimeConfig);
            Request httpRequest = new Request.Builder()
                .url(getChatCompletionsUrl(runtimeConfig))
                .header("Authorization", "Bearer " + runtimeConfig.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(request), JSON))
                .build();

            try (Response response = okHttpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new DomainException("LLM_API_ERROR", buildErrorMessage(response));
                }
                if (response.body() == null) {
                    throw new DomainException("LLM_EMPTY_RESPONSE", "LLM response body is empty");
                }
                return objectMapper.readValue(response.body().string(), ChatCompletionResponse.class);
            }
        } catch (IOException ex) {
            throw new DomainException("LLM_IO_ERROR", "Failed to call LLM provider");
        }
    }

    private String buildErrorMessage(Response response) {
        StringBuilder builder = new StringBuilder();
        builder.append("LLM request failed with status ").append(response.code());
        try {
            if (response.body() != null) {
                String errorBody = response.body().string();
                if (errorBody != null && !errorBody.trim().isEmpty()) {
                    builder.append(", body=").append(errorBody);
                }
            }
        } catch (IOException ignored) {
            builder.append(", body=<unavailable>");
        }
        return builder.toString();
    }

    private static OkHttpClient buildClient(LlmRuntimeConfig runtimeConfig) {
        int timeoutMs = runtimeConfig.getTimeoutMs() == null ? 30000 : runtimeConfig.getTimeoutMs();
        return new OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }

    private String getBaseUrl(LlmRuntimeConfig runtimeConfig) {
        String url = runtimeConfig.getBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String getChatCompletionsUrl(LlmRuntimeConfig runtimeConfig) {
        String baseUrl = getBaseUrl(runtimeConfig);
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }
}
