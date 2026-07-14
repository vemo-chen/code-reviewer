package com.vemo.codereview.llm.service;

import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
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
        return review(runtimeConfig, reviewPrompt);
    }

    public ChatCompletionResponse review(LlmRuntimeConfig runtimeConfig, ReviewPromptPayload reviewPrompt) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(runtimeConfig.getModelName());
        request.setTemperature(runtimeConfig.getTemperature() == null ? null : runtimeConfig.getTemperature().doubleValue());
        request.setMaxTokens(runtimeConfig.getMaxTokens());
        applyThinkingConfig(request, runtimeConfig);
        applyResponseFormat(request, runtimeConfig);
        request.setMessages(buildMessages(reviewPrompt));
        return chatModelClientFactory.getClient(runtimeConfig.getProviderType()).chatCompletion(request, runtimeConfig);
    }

    private void applyThinkingConfig(ChatCompletionRequest request, LlmRuntimeConfig runtimeConfig) {
        if (!"DEEPSEEK".equalsIgnoreCase(runtimeConfig.getProviderCode())) {
            request.setThinking(null);
            return;
        }
        request.setThinking(new ChatCompletionRequest.Thinking(
            Boolean.TRUE.equals(runtimeConfig.getThinkingEnabled()) ? "enabled" : "disabled"
        ));
    }

    private void applyResponseFormat(ChatCompletionRequest request, LlmRuntimeConfig runtimeConfig) {
        if (!"DEEPSEEK".equalsIgnoreCase(runtimeConfig.getProviderCode())) {
            request.setResponseFormat(null);
            return;
        }
        request.setResponseFormat(new ChatCompletionRequest.ResponseFormat("json_object"));
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
                appendSemanticContext(builder, file);
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void appendSemanticContext(StringBuilder builder, ReviewPromptPayload.PromptFilePayload file) {
        if (file.getContextStatus() == null && file.getContextSnippets() == null) {
            return;
        }
        builder.append("Context Status: ").append(file.getContextStatus()).append('\n');
        if (file.getSkipReason() != null) {
            builder.append("Context Skip Reason: ").append(file.getSkipReason()).append('\n');
        }
        if (file.getRiskHints() != null && !file.getRiskHints().isEmpty()) {
            builder.append("Risk Hints: ").append(String.join(", ", file.getRiskHints())).append('\n');
        }
        if (Boolean.TRUE.equals(file.getContextTruncated())) {
            builder.append("Context Truncated: true").append('\n');
        }
        if (file.getContextSnippets() == null || file.getContextSnippets().isEmpty()) {
            return;
        }
        builder.append("Semantic Context:").append('\n');
        for (ReviewCodeSnippet snippet : file.getContextSnippets()) {
            builder.append("Snippet: ").append(snippet.getTitle())
                .append(", lines ").append(snippet.getStartLine()).append("-").append(snippet.getEndLine()).append('\n');
            builder.append(snippet.getContent()).append('\n');
        }
    }
}
