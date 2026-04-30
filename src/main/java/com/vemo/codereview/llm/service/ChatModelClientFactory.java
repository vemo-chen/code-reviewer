package com.vemo.codereview.llm.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.client.AnthropicClient;
import com.vemo.codereview.llm.client.ChatModelClient;
import com.vemo.codereview.llm.client.OpenAiCompatibleClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatModelClientFactory {

    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final AnthropicClient anthropicClient;

    public ChatModelClientFactory(
        OpenAiCompatibleClient openAiCompatibleClient,
        AnthropicClient anthropicClient) {
        this.openAiCompatibleClient = openAiCompatibleClient;
        this.anthropicClient = anthropicClient;
    }

    public ChatModelClient getClient(String providerType) {
        String resolvedProviderType = StringUtils.hasText(providerType) ? providerType.trim() : "OPENAI_COMPATIBLE";
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(resolvedProviderType)) {
            return openAiCompatibleClient;
        }
        if ("ANTHROPIC".equalsIgnoreCase(resolvedProviderType)) {
            return anthropicClient;
        }
        throw new DomainException("LLM_PROVIDER_TYPE_UNSUPPORTED", "Unsupported LLM provider type: " + providerType);
    }
}
