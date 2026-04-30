package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.ReviewScoreService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReviewScoreServiceTest {

    @Test
    void shouldApplyHardRuleDeductionOnlyOnceForSameProblemTypeAcrossFiles() {
        ReviewCommentDraft first = new ReviewCommentDraft();
        first.setCategory("Project hard rule");
        first.setSeverity("HIGH");
        first.setMessage("Data class annotation violation");
        first.setFilePath("a.java");

        ReviewCommentDraft second = new ReviewCommentDraft();
        second.setCategory("Project hard rule");
        second.setSeverity("HIGH");
        second.setMessage("Data class annotation violation");
        second.setFilePath("b.java");

        ReviewCommentDraft third = new ReviewCommentDraft();
        third.setCategory("Project hard rule");
        third.setSeverity("MEDIUM");
        third.setMessage("Bean injection convention violation");
        third.setFilePath("c.java");

        ReviewSummary summary = new ReviewSummary();
        summary.setSuggestedScore(90);
        summary.setComments(Arrays.asList(first, second, third));

        ReviewScoreService service = new ReviewScoreService();
        service.applyScores(summary);

        assertEquals(Integer.valueOf(10), summary.getDeductionScore());
        assertEquals(Integer.valueOf(80), summary.getFinalScore());
        assertEquals("Suggested score: 90; Deducted 6 points for 1 HIGH hard-rule issue(s); Deducted 4 points for 1 MEDIUM hard-rule issue(s); Final score: 80", summary.getScoreReason());
    }
}
