package com.vemo.codereview.review.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewBatchPlanner {
    private final ReviewContextBudgetService budgetService;

    public ReviewBatchPlanner(ReviewContextBudgetService budgetService) {
        this.budgetService = budgetService;
    }

    public List<ReviewExecutionBatch> plan(List<ReviewSemanticUnit> units, Integer maxOutputTokens) {
        if (maxOutputTokens == null || maxOutputTokens <= 0) {
            throw new DomainException("LLM_MAX_TOKENS_INVALID", "Model maxTokens must be positive");
        }
        List<ReviewExecutionBatch> result = new ArrayList<ReviewExecutionBatch>();
        if (units == null) return result;
        ReviewExecutionBatch batch = new ReviewExecutionBatch();
        for (ReviewSemanticUnit original : units) {
            ReviewSemanticUnit unit = truncateIfNecessary(original, budgetService.maxTotalChars());
            batch.add(unit);
        }
        if (!batch.isEmpty()) result.add(batch);
        return result;
    }

    private ReviewSemanticUnit truncateIfNecessary(ReviewSemanticUnit unit, int maxChars) {
        int diffLength = unit.getDiff() == null ? 0 : unit.getDiff().length();
        int codeLength = unit.getExpandedCode() == null ? 0 : unit.getExpandedCode().length();
        if (diffLength + codeLength <= maxChars) return unit;
        int remaining = Math.max(0, maxChars - diffLength);
        if (unit.getExpandedCode() != null && unit.getExpandedCode().length() > remaining) {
            unit.setExpandedCode(unit.getExpandedCode().substring(0, remaining));
            unit.setTruncated(Boolean.TRUE);
        }
        return unit;
    }
}
