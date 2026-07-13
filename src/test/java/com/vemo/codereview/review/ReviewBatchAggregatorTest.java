package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.model.AggregatedReviewOutput;
import com.vemo.codereview.review.model.ReviewBatchOutput;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.ReviewBatchAggregator;
import com.vemo.codereview.review.service.ReviewScoreService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ReviewBatchAggregatorTest {

    @Test
    void shouldAggregateWorstScoreRiskUniqueCommentsAndUsage() throws Exception {
        ReviewBatchAggregator aggregator = new ReviewBatchAggregator(new ReviewScoreService(), new ObjectMapper());

        AggregatedReviewOutput output = aggregator.aggregate(Arrays.asList(
            output(1, 90, "LOW", "hash-a", 10, 2, 5, "a"),
            output(2, 70, "HIGH", "hash-a", 20, 3, 7, "b"),
            output(3, 80, "MEDIUM", "hash-b", 30, 4, 9, "c")));

        assertEquals(Integer.valueOf(70), output.getSummary().getSuggestedScore());
        assertEquals("HIGH", output.getSummary().getRiskLevel());
        assertEquals(2, output.getSummary().getComments().size());
        assertEquals(Integer.valueOf(60), output.getInputTokens());
        assertEquals(Integer.valueOf(9), output.getOutputTokens());
        assertEquals(Long.valueOf(21), output.getLatencyMs());
        assertTrue(new ObjectMapper().readTree(output.getRawResponse()).isArray());
    }

    @Test
    void shouldLimitRawTraceByUtf8BytesWithoutBreakingJson() throws Exception {
        ReviewBatchAggregator aggregator = new ReviewBatchAggregator(new ReviewScoreService(), new ObjectMapper());
        String content = String.join("", Collections.nCopies(30000, "中😀"));

        AggregatedReviewOutput output = aggregator.aggregate(Arrays.asList(
            output(1, 90, "LOW", "a", 1, 1, 1, content),
            output(2, 80, "MEDIUM", "b", 1, 1, 1, content)));

        assertTrue(output.getRawResponse().getBytes(StandardCharsets.UTF_8).length <= 60000);
        assertTrue(new ObjectMapper().readTree(output.getRawResponse()).toString().contains("truncated"));
    }

    private ReviewBatchOutput output(int index, int score, String risk, String hash,
                                     int input, int completion, long latency, String content) {
        ReviewCommentDraft comment = new ReviewCommentDraft();
        comment.setFilePath("src/A.java");
        comment.setLine(index);
        comment.setCategory("General");
        comment.setMessage(hash);
        comment.setCommentHash(hash);
        ReviewSummary summary = new ReviewSummary();
        summary.setSuggestedScore(score);
        summary.setRiskLevel(risk);
        summary.setSummary("summary-" + index);
        summary.setBriefSummary("brief-" + index);
        summary.setAdvice("advice-" + index);
        summary.setComments(Collections.singletonList(comment));
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("model");
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        usage.setPromptTokens(input);
        usage.setCompletionTokens(completion);
        response.setUsage(usage);
        ReviewBatchOutput output = new ReviewBatchOutput();
        output.setBatchIndex(index);
        output.setSummary(summary);
        output.setResponse(response);
        output.setLatencyMs(latency);
        output.setRawContent(content);
        return output;
    }
}
