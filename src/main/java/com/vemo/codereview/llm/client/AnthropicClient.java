package com.vemo.codereview.llm.client;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import org.springframework.stereotype.Component;

@Component
public class AnthropicClient implements ChatModelClient {

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request, LlmRuntimeConfig runtimeConfig) {
        throw new DomainException(
            "LLM_PROVIDER_UNSUPPORTED",
            "Anthropic provider is not implemented yet for runtime chat completion"
        );
    }
}
