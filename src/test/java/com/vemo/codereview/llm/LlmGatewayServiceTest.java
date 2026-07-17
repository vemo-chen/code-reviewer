package com.vemo.codereview.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vemo.codereview.llm.client.ChatModelClient;
import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.llm.service.ChatModelClientFactory;
import com.vemo.codereview.llm.service.LlmConfigResolverService;
import com.vemo.codereview.llm.service.LlmGatewayService;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LlmGatewayServiceTest {

    @Test
    void shouldRequestJsonOutputForNonDeepSeekProvider() {
        LlmConfigResolverService resolver = mock(LlmConfigResolverService.class);
        ChatModelClientFactory factory = mock(ChatModelClientFactory.class);
        ChatModelClient client = mock(ChatModelClient.class);
        LlmGatewayService service = new LlmGatewayService(resolver, factory);
        LlmRuntimeConfig config = new LlmRuntimeConfig();
        config.setProviderCode("OPENAI");
        config.setProviderType("OPENAI_COMPATIBLE");
        config.setModelName("gpt-5.5");
        config.setMaxTokens(1024);
        config.setTemperature(new BigDecimal("0.1"));
        when(factory.getClient("OPENAI_COMPATIBLE")).thenReturn(client);
        when(client.chatCompletion(any(ChatCompletionRequest.class), any(LlmRuntimeConfig.class)))
            .thenReturn(new ChatCompletionResponse());

        service.review(config, new ReviewPromptPayload());

        org.mockito.ArgumentCaptor<ChatCompletionRequest> requestCaptor =
            org.mockito.ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(client).chatCompletion(requestCaptor.capture(), org.mockito.ArgumentMatchers.same(config));
        assertEquals("json_object", requestCaptor.getValue().getResponseFormat().getType());
    }
}
