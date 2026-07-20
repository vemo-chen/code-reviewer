package com.vemo.codereview.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.llm.client.OpenAiCompatibleClient;
import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldSendChatCompletionRequestAndParseResponse() throws Exception {
        OpenAiCompatibleClient openAiCompatibleClient = createClient();
        LlmRuntimeConfig runtimeConfig = buildRuntimeConfig(mockWebServer.url("").toString());
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"chatcmpl-1\",\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120}}"));

        ChatCompletionResponse response = openAiCompatibleClient.chatCompletion(buildRequest(), runtimeConfig);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String requestBody = recordedRequest.getBody().readUtf8();
        assertEquals("/v1/chat/completions", recordedRequest.getPath());
        assertEquals("Bearer llm-key", recordedRequest.getHeader("Authorization"));
        assertTrue(requestBody.contains("\"model\":\"deepseek-chat\""));
        assertTrue(requestBody.contains("\"thinking\":{\"type\":\"enabled\"}"));
        assertTrue(requestBody.contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertEquals("deepseek-chat", response.getModel());
        assertEquals("{\"summary\":\"ok\"}", response.getFirstContent());
        assertEquals(Integer.valueOf(120), response.getUsage().getTotalTokens());
    }

    @Test
    void shouldNotDuplicateV1PathWhenBaseUrlAlreadyContainsVersion() throws Exception {
        OpenAiCompatibleClient openAiCompatibleClient = createClient();
        LlmRuntimeConfig runtimeConfig = buildRuntimeConfig(mockWebServer.url("/v1").toString());
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"chatcmpl-1\",\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":1,\"total_tokens\":11}}"));

        openAiCompatibleClient.chatCompletion(buildRequest(), runtimeConfig);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/v1/chat/completions", recordedRequest.getPath());
    }

    @Test
    void shouldNotSerializeFullRequestForDebugLogging() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(FastjsonSensitiveRequest.class, IgnoreMessagesMixin.class);
        OpenAiCompatibleClient openAiCompatibleClient = new OpenAiCompatibleClient(mapper);
        LlmRuntimeConfig runtimeConfig = buildRuntimeConfig(mockWebServer.url("").toString());
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"chatcmpl-1\",\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":1,\"total_tokens\":11}}"));

        openAiCompatibleClient.chatCompletion(buildRequestWithFastjsonOnlyGetter(), runtimeConfig);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/v1/chat/completions", recordedRequest.getPath());
    }

    private OpenAiCompatibleClient createClient() {
        return new OpenAiCompatibleClient(objectMapper);
    }

    private LlmRuntimeConfig buildRuntimeConfig(String baseUrl) {
        LlmRuntimeConfig runtimeConfig = new LlmRuntimeConfig();
        runtimeConfig.setBaseUrl(baseUrl);
        runtimeConfig.setApiKey("llm-key");
        runtimeConfig.setModelName("deepseek-chat");
        runtimeConfig.setTimeoutMs(3000);
        runtimeConfig.setMaxTokens(2048);
        runtimeConfig.setTemperature(new BigDecimal("0.1"));
        runtimeConfig.setThinkingEnabled(Boolean.TRUE);
        return runtimeConfig;
    }

    private ChatCompletionRequest buildRequest() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("deepseek-chat");
        request.setTemperature(0.1D);
        request.setMaxTokens(2048);
        request.setThinking(new ChatCompletionRequest.Thinking("enabled"));
        request.setResponseFormat(new ChatCompletionRequest.ResponseFormat("json_object"));
        request.setMessages(Arrays.asList(
            new ChatCompletionRequest.Message("system", "system prompt"),
            new ChatCompletionRequest.Message("user", "user prompt")
        ));
        return request;
    }

    private ChatCompletionRequest buildRequestWithFastjsonOnlyGetter() {
        ChatCompletionRequest request = new FastjsonSensitiveRequest();
        request.setModel("deepseek-chat");
        request.setTemperature(0.1D);
        request.setMaxTokens(2048);
        request.setMessages(Arrays.asList(
            new ChatCompletionRequest.Message("system", "system prompt"),
            new ChatCompletionRequest.Message("user", "user prompt")
        ));
        return request;
    }

    private static class FastjsonSensitiveRequest extends ChatCompletionRequest {
        @Override
        public List<Message> getMessages() {
            throw new AssertionError("full request debug serialization should not inspect message content");
        }
    }

    private abstract static class IgnoreMessagesMixin {
        @JsonIgnore
        public abstract List<ChatCompletionRequest.Message> getMessages();
    }
}
