package com.vemo.codereview.review.model;

import com.vemo.codereview.llm.model.ChatCompletionResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewBatchOutput {
    private int batchIndex;
    private ReviewSummary summary;
    private ChatCompletionResponse response;
    private long latencyMs;
    private String rawContent;
}
