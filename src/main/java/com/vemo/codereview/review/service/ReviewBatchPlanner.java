package com.vemo.codereview.review.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewBatchPlanner {
    public List<ReviewExecutionBatch> plan(List<ReviewSemanticUnit> units, Integer maxOutputTokens) {
        if (maxOutputTokens == null || maxOutputTokens <= 0) {
            throw new DomainException("LLM_MAX_TOKENS_INVALID", "Model maxTokens must be positive");
        }
        List<ReviewExecutionBatch> result = new ArrayList<ReviewExecutionBatch>();
        if (units == null) return result;
        ReviewExecutionBatch batch = new ReviewExecutionBatch();
        for (ReviewSemanticUnit original : units) {
            batch.add(original);
        }
        if (!batch.isEmpty()) result.add(batch);
        return result;
    }
}
