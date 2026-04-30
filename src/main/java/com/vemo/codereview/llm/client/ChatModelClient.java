package com.vemo.codereview.llm.client;

import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;

public interface ChatModelClient {

    ChatCompletionResponse chatCompletion(ChatCompletionRequest request, LlmRuntimeConfig runtimeConfig);
}
