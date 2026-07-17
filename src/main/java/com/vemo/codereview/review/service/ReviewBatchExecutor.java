package com.vemo.codereview.review.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.llm.service.LlmGatewayService;
import com.vemo.codereview.review.model.ReviewBatchOutput;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewPromptMode;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReviewBatchExecutor {
    private final PromptBuilderService promptBuilderService;
    private final LlmGatewayService gatewayService;
    private final ReviewResponseParser responseParser;

    public ReviewBatchExecutor(PromptBuilderService promptBuilderService, LlmGatewayService gatewayService,
                               ReviewResponseParser responseParser) {
        this.promptBuilderService = promptBuilderService;
        this.gatewayService = gatewayService;
        this.responseParser = responseParser;
    }

    public List<ReviewBatchOutput> execute(Long taskId, Long projectId, ReviewExecutionContext context,
                                           List<ReviewExecutionBatch> batches, LlmRuntimeConfig runtimeConfig) {
        List<ReviewBatchOutput> result = new ArrayList<ReviewBatchOutput>();
        for (int index = 0; index < batches.size(); index++) {
            result.addAll(executeRecovering(taskId, context, batches.get(index), runtimeConfig,
                String.valueOf(index + 1), 0));
        }
        for (int index = 0; index < result.size(); index++) result.get(index).setBatchIndex(index + 1);
        return result;
    }

    private List<ReviewBatchOutput> executeRecovering(Long taskId, ReviewExecutionContext context,
                                                       ReviewExecutionBatch batch, LlmRuntimeConfig config,
                                                       String batchPath, int splitDepth) {
        try {
            return Collections.singletonList(executeOnce(taskId, context, batch, config,
                ReviewPromptMode.NORMAL, batchPath, splitDepth));
        } catch (DomainException ex) {
            if (!isSplittableFailure(ex)) throw ex;
            if (batch.getUnits().size() > 1) {
                ReviewExecutionBatch.Split split = batch.splitHalf();
                List<ReviewBatchOutput> result = new ArrayList<ReviewBatchOutput>();
                result.addAll(executeRecovering(taskId, context, split.getLeft(), config, batchPath + ".1", splitDepth + 1));
                result.addAll(executeRecovering(taskId, context, split.getRight(), config, batchPath + ".2", splitDepth + 1));
                return result;
            }
            try {
                return Collections.singletonList(executeOnce(taskId, context, batch, config,
                    ReviewPromptMode.COMPACT, batchPath + ".compact", splitDepth));
            } catch (DomainException compact) {
                if (isSplittableFailure(compact)) {
                    throw new DomainException("REVIEW_SINGLE_UNIT_OUTPUT_TRUNCATED",
                        "Single review unit exceeded the configured model output limit");
                }
                throw compact;
            }
        }
    }

    private boolean isSplittableFailure(DomainException ex) {
        if ("REVIEW_RESULT_TRUNCATED".equals(ex.getCode())) return true;
        if (!"LLM_API_ERROR".equals(ex.getCode())) return false;
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("context") || message.contains("token") || message.contains("prompt")
            || message.contains("input") || message.contains("length") || message.contains("too large");
    }

    private ReviewBatchOutput executeOnce(Long taskId, ReviewExecutionContext context, ReviewExecutionBatch batch,
                                          LlmRuntimeConfig config, ReviewPromptMode mode,
                                          String batchPath, int splitDepth) {
        ReviewPromptPayload prompt = promptBuilderService.build(context, batch, 1, 1, mode);
        long start = System.nanoTime();
        ChatCompletionResponse response = gatewayService.review(config, prompt);
        long latency = (System.nanoTime() - start) / 1_000_000L;
        String finishReason = finishReason(response);
        log.info("review batch finished. taskId={}, model={}, maxTokens={}, batchPath={}, unitCount={}, "
                + "promptMode={}, finishReason={}, inputTokens={}, outputTokens={}, splitDepth={}",
            taskId, config.getModelName(), config.getMaxTokens(), batchPath, batch.getUnits().size(), mode,
            finishReason, inputTokens(response), outputTokens(response), splitDepth);
        if ("length".equalsIgnoreCase(finishReason)) {
            throw new DomainException("REVIEW_RESULT_TRUNCATED", "Model response reached max output tokens");
        }
        ReviewBatchOutput output = new ReviewBatchOutput();
        output.setSummary(responseParser.parse(response));
        output.setResponse(response);
        output.setLatencyMs(latency);
        output.setRawContent(response == null ? null : response.getFirstContent());
        return output;
    }

    private String finishReason(ChatCompletionResponse response) {
        return response == null || response.getChoices() == null || response.getChoices().isEmpty()
            || response.getChoices().get(0) == null ? null : response.getChoices().get(0).getFinishReason();
    }
    private Integer inputTokens(ChatCompletionResponse response) { return response == null || response.getUsage() == null ? null : response.getUsage().getPromptTokens(); }
    private Integer outputTokens(ChatCompletionResponse response) { return response == null || response.getUsage() == null ? null : response.getUsage().getCompletionTokens(); }
}
