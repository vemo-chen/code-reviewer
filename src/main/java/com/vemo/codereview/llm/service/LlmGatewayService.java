package com.vemo.codereview.llm.service;

import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LlmGatewayService {

    private final LlmConfigResolverService llmConfigResolverService;
    private final ChatModelClientFactory chatModelClientFactory;

    public LlmGatewayService(
        LlmConfigResolverService llmConfigResolverService,
        ChatModelClientFactory chatModelClientFactory) {
        this.llmConfigResolverService = llmConfigResolverService;
        this.chatModelClientFactory = chatModelClientFactory;
    }

    public ChatCompletionResponse review(Long projectId, ReviewPromptPayload reviewPrompt) {
        LlmRuntimeConfig runtimeConfig = llmConfigResolverService.resolve(projectId);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(runtimeConfig.getModelName());
        request.setTemperature(runtimeConfig.getTemperature() == null ? null : runtimeConfig.getTemperature().doubleValue());
        request.setMaxTokens(runtimeConfig.getMaxTokens());
        request.setMessages(buildMessages(reviewPrompt));
        return chatModelClientFactory.getClient(runtimeConfig.getProviderType()).chatCompletion(request, runtimeConfig);
    }

    private List<ChatCompletionRequest.Message> buildMessages(ReviewPromptPayload reviewPrompt) {
        List<ChatCompletionRequest.Message> messages = new ArrayList<ChatCompletionRequest.Message>();
        messages.add(new ChatCompletionRequest.Message("system", reviewPrompt.getSystemPrompt()));
        messages.add(new ChatCompletionRequest.Message("user", buildUserContent(reviewPrompt)));
        return messages;
    }

    private String buildUserContent(ReviewPromptPayload reviewPrompt) {
        StringBuilder builder = new StringBuilder();
        builder.append(reviewPrompt.getUserPrompt()).append("\n\n");
        if (reviewPrompt.getFiles() != null) {
            for (ReviewPromptPayload.PromptFilePayload file : reviewPrompt.getFiles()) {
                builder.append("File: ").append(file.getFilePath()).append('\n');
                builder.append("Change Type: ").append(file.getChangeType()).append('\n');
                if (file.getDiffChunks() != null) {
                    for (int i = 0; i < file.getDiffChunks().size(); i++) {
                        builder.append("Chunk ").append(i + 1).append(':').append('\n');
                        builder.append(file.getDiffChunks().get(i)).append('\n');
                    }
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }
}
