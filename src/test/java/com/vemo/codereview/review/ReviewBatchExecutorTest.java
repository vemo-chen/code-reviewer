package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.llm.service.LlmGatewayService;
import com.vemo.codereview.review.model.ReviewBatchOutput;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.PromptBuilderService;
import com.vemo.codereview.review.service.ReviewBatchExecutor;
import com.vemo.codereview.review.service.ReviewContextBudgetService;
import com.vemo.codereview.review.service.ReviewResponseParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewBatchExecutorTest {
    @Mock private PromptBuilderService promptBuilderService;
    @Mock private LlmGatewayService gatewayService;
    @Mock private ReviewResponseParser parser;
    @Mock private ReviewContextBudgetService budgetService;

    @Test
    void shouldSplitLengthTruncatedBatchLeftThenRight() {
        ReviewBatchExecutor executor = new ReviewBatchExecutor(promptBuilderService, gatewayService, parser, budgetService);
        ReviewExecutionBatch batch = batch("u1", "u2", "u3", "u4");
        LlmRuntimeConfig config = new LlmRuntimeConfig();
        config.setModelName("model");
        config.setMaxTokens(8192);
        ChatCompletionResponse truncated = response("length", "partial");
        ChatCompletionResponse left = response("stop", "left");
        ChatCompletionResponse right = response("stop", "right");
        when(budgetService.maxRequestBodyBytes()).thenReturn(1024);
        when(gatewayService.requestBodyBytes(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class))).thenReturn(100L);
        when(promptBuilderService.build(any(), any(), any(Integer.class), any(Integer.class), any())).thenReturn(new ReviewPromptPayload());
        when(gatewayService.review(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class)))
            .thenReturn(truncated, left, right);
        when(parser.parse(left)).thenReturn(new ReviewSummary());
        when(parser.parse(right)).thenReturn(new ReviewSummary());

        List<ReviewBatchOutput> outputs = executor.execute(1L, 2L, new ReviewExecutionContext(),
            Collections.singletonList(batch), config);

        assertEquals(2, outputs.size());
        assertEquals(1, outputs.get(0).getBatchIndex());
        assertEquals(2, outputs.get(1).getBatchIndex());
        verify(gatewayService, times(3)).review(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class));
    }

    @Test
    void shouldSplitRequestTooLargeProviderErrorLeftThenRight() {
        ReviewBatchExecutor executor = new ReviewBatchExecutor(promptBuilderService, gatewayService, parser, budgetService);
        ReviewExecutionBatch batch = batch("u1", "u2", "u3", "u4");
        LlmRuntimeConfig config = new LlmRuntimeConfig();
        config.setModelName("model");
        config.setMaxTokens(8192);
        DomainException requestTooLarge = new DomainException("LLM_API_ERROR",
            "LLM request failed with status 413, body={\"error\":{\"message\":\"Request body size exceeds maximum allowed sized\","
                + "\"type\":\"RequestTooLarge\",\"code\":\"RequestTooLarge\"}}");
        ChatCompletionResponse left = response("stop", "left");
        ChatCompletionResponse right = response("stop", "right");
        when(budgetService.maxRequestBodyBytes()).thenReturn(1024);
        when(gatewayService.requestBodyBytes(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class))).thenReturn(100L);
        when(promptBuilderService.build(any(), any(), any(Integer.class), any(Integer.class), any()))
            .thenReturn(new ReviewPromptPayload());
        when(gatewayService.review(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class)))
            .thenThrow(requestTooLarge)
            .thenReturn(left, right);
        when(parser.parse(left)).thenReturn(new ReviewSummary());
        when(parser.parse(right)).thenReturn(new ReviewSummary());

        List<ReviewBatchOutput> outputs = executor.execute(1L, 2L, new ReviewExecutionContext(),
            Collections.singletonList(batch), config);

        assertEquals(2, outputs.size());
        verify(gatewayService, times(3)).review(any(LlmRuntimeConfig.class), any(ReviewPromptPayload.class));
    }

    @Test
    void shouldSplitBeforeProviderCallWhenRequestBodyExceedsBudget() {
        ReviewBatchExecutor executor = new ReviewBatchExecutor(promptBuilderService, gatewayService, parser, budgetService);
        ReviewExecutionBatch batch = batch("u1", "u2");
        LlmRuntimeConfig config = new LlmRuntimeConfig();
        config.setModelName("model");
        config.setMaxTokens(8192);
        ReviewPromptPayload fullPrompt = new ReviewPromptPayload();
        ReviewPromptPayload leftPrompt = new ReviewPromptPayload();
        ReviewPromptPayload rightPrompt = new ReviewPromptPayload();
        ChatCompletionResponse left = response("stop", "left");
        ChatCompletionResponse right = response("stop", "right");
        when(budgetService.maxRequestBodyBytes()).thenReturn(200);
        when(promptBuilderService.build(any(), any(), any(Integer.class), any(Integer.class), any()))
            .thenReturn(fullPrompt, leftPrompt, rightPrompt);
        when(gatewayService.requestBodyBytes(config, fullPrompt)).thenReturn(201L);
        when(gatewayService.requestBodyBytes(config, leftPrompt)).thenReturn(100L);
        when(gatewayService.requestBodyBytes(config, rightPrompt)).thenReturn(100L);
        when(gatewayService.review(config, leftPrompt)).thenReturn(left);
        when(gatewayService.review(config, rightPrompt)).thenReturn(right);
        when(parser.parse(left)).thenReturn(new ReviewSummary());
        when(parser.parse(right)).thenReturn(new ReviewSummary());

        List<ReviewBatchOutput> outputs = executor.execute(1L, 2L, new ReviewExecutionContext(),
            Collections.singletonList(batch), config);

        assertEquals(2, outputs.size());
        verify(gatewayService, times(0)).review(config, fullPrompt);
        verify(gatewayService).review(config, leftPrompt);
        verify(gatewayService).review(config, rightPrompt);
    }

    private ReviewExecutionBatch batch(String... keys) {
        ReviewExecutionBatch batch = new ReviewExecutionBatch();
        for (String key : keys) {
            ReviewSemanticUnit unit = new ReviewSemanticUnit();
            unit.setUnitKey(key);
            unit.setFilePath(key + ".java");
            batch.add(unit);
        }
        return batch;
    }

    private ChatCompletionResponse response(String finishReason, String content) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("model");
        ChatCompletionResponse.Message message = new ChatCompletionResponse.Message();
        message.setContent(content);
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setFinishReason(finishReason);
        choice.setMessage(message);
        response.setChoices(Arrays.asList(choice));
        return response;
    }
}
