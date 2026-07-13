package com.vemo.codereview.review.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregatedReviewOutput {
    private ReviewSummary summary;
    private String providerName;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
    private String rawResponse;
}
