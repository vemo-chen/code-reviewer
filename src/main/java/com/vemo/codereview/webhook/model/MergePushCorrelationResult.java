package com.vemo.codereview.webhook.model;

public final class MergePushCorrelationResult {

    private final MergePushDecision decision;
    private final Long reviewedMrTaskId;

    private MergePushCorrelationResult(MergePushDecision decision, Long reviewedMrTaskId) {
        this.decision = decision;
        this.reviewedMrTaskId = reviewedMrTaskId;
    }

    public static MergePushCorrelationResult reviewed(Long taskId) {
        return new MergePushCorrelationResult(MergePushDecision.SKIP_ALREADY_REVIEWED, taskId);
    }

    public static MergePushCorrelationResult decision(MergePushDecision decision) {
        return new MergePushCorrelationResult(decision, null);
    }

    public MergePushDecision getDecision() {
        return decision;
    }

    public Long getReviewedMrTaskId() {
        return reviewedMrTaskId;
    }
}
