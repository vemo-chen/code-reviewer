package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.ReviewResultPersistenceService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:review-result-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class ReviewResultPersistenceServiceTest {

    @Autowired
    private ReviewResultPersistenceService reviewResultPersistenceService;

    @Autowired
    private ReviewResultStoreMapper codeReviewResultMapper;

    @Autowired
    private ReviewCommentStoreMapper codeReviewCommentMapper;

    @Test
    void shouldPersistResultSummaryCommentsAndScores() {
        ReviewCommentDraft draft = new ReviewCommentDraft();
        draft.setFilePath("src/main/java/com/example/ReviewService.java");
        draft.setLine(21);
        draft.setSeverity("HIGH");
        draft.setCategory("NULL_POINTER");
        draft.setMessage("Potential null dereference");
        draft.setSuggestion("Add null check");
        draft.setCommentHash("hash-1");

        ReviewSummary summary = new ReviewSummary();
        summary.setSuggestedScore(88);
        summary.setDeductionScore(12);
        summary.setFinalScore(76);
        summary.setSummary("Found one high risk issue");
        summary.setBriefSummary("High risk issue detected");
        summary.setRiskLevel("HIGH");
        summary.setScoreReason("Deducted 12 points for one HIGH issue");
        summary.setAdvice("Fix before merge");
        summary.setComments(Collections.singletonList(draft));

        ChatCompletionResponse response = buildResponse("{\"summary\":\"Found one high risk issue\",\"briefSummary\":\"High risk issue detected\"}", 120, 35, 155);

        CodeReviewResultEntity result = reviewResultPersistenceService.persist(
            101L, "openai-compatible", "deepseek-chat", summary, response);

        assertNotNull(result.getId());

        CodeReviewResultEntity savedResult = codeReviewResultMapper.selectById(result.getId());
        CodeReviewCommentEntity savedComment = codeReviewCommentMapper.selectOne(
            new QueryWrapper<CodeReviewCommentEntity>().eq("result_id", result.getId()));

        assertEquals(Integer.valueOf(88), savedResult.getSuggestedScore());
        assertEquals(Integer.valueOf(12), savedResult.getDeductionScore());
        assertEquals(Integer.valueOf(76), savedResult.getFinalScore());
        assertEquals("Deducted 12 points for one HIGH issue", savedResult.getScoreReason());
        assertEquals("HIGH", savedResult.getRiskLevel());
        assertEquals("Found one high risk issue", savedResult.getSummary());
        assertEquals("High risk issue detected", savedResult.getBriefSummary());
        assertEquals(Integer.valueOf(120), savedResult.getInputTokens());
        assertEquals(Integer.valueOf(35), savedResult.getOutputTokens());

        assertNotNull(savedComment);
        assertEquals(result.getId(), savedComment.getResultId());
        assertEquals("HIGH", savedComment.getSeverity());
        assertEquals("hash-1", savedComment.getCommentHash());
    }

    @Test
    void shouldReplaceExistingResultAndCommentsForSameTask() {
        ReviewSummary firstSummary = buildSummary(
            90,
            0,
            90,
            "First summary",
            "First brief summary",
            "HIGH",
            "First score reason",
            "hash-old",
            "Old issue"
        );
        ReviewSummary secondSummary = buildSummary(
            70,
            12,
            58,
            "Second summary",
            "Second brief summary",
            "MEDIUM",
            "Second score reason",
            "hash-new",
            "New issue"
        );

        reviewResultPersistenceService.persist(
            202L,
            "openai-compatible",
            "deepseek-chat",
            firstSummary,
            buildResponse("{\"summary\":\"First summary\"}", 100, 20, 120)
        );

        CodeReviewResultEntity latestResult = reviewResultPersistenceService.persist(
            202L,
            "openai-compatible",
            "deepseek-chat",
            secondSummary,
            buildResponse("{\"summary\":\"Second summary\"}", 110, 30, 140)
        );

        Long resultCount = codeReviewResultMapper.selectCount(
            new QueryWrapper<CodeReviewResultEntity>().eq("task_id", 202L)
        );
        Long commentCount = codeReviewCommentMapper.selectCount(
            new QueryWrapper<CodeReviewCommentEntity>().eq("result_id", latestResult.getId())
        );
        Long oldCommentCount = codeReviewCommentMapper.selectCount(
            new QueryWrapper<CodeReviewCommentEntity>().eq("comment_hash", "hash-old")
        );

        CodeReviewResultEntity savedResult = codeReviewResultMapper.selectById(latestResult.getId());
        CodeReviewCommentEntity savedComment = codeReviewCommentMapper.selectOne(
            new QueryWrapper<CodeReviewCommentEntity>().eq("result_id", latestResult.getId())
        );

        assertEquals(Long.valueOf(1L), resultCount);
        assertEquals(Long.valueOf(1L), commentCount);
        assertEquals(Long.valueOf(0L), oldCommentCount);
        assertEquals("Second summary", savedResult.getSummary());
        assertEquals("Second brief summary", savedResult.getBriefSummary());
        assertEquals("hash-new", savedComment.getCommentHash());
        assertEquals("New issue", savedComment.getMessage());
    }

    private ReviewSummary buildSummary(
        Integer suggestedScore,
        Integer deductionScore,
        Integer finalScore,
        String summaryText,
        String briefSummary,
        String riskLevel,
        String scoreReason,
        String commentHash,
        String message) {
        ReviewCommentDraft draft = new ReviewCommentDraft();
        draft.setFilePath("src/main/java/com/example/ReviewService.java");
        draft.setLine(21);
        draft.setSeverity("HIGH");
        draft.setCategory("NULL_POINTER");
        draft.setMessage(message);
        draft.setSuggestion("Add null check");
        draft.setCommentHash(commentHash);

        ReviewSummary summary = new ReviewSummary();
        summary.setSuggestedScore(suggestedScore);
        summary.setDeductionScore(deductionScore);
        summary.setFinalScore(finalScore);
        summary.setSummary(summaryText);
        summary.setBriefSummary(briefSummary);
        summary.setRiskLevel(riskLevel);
        summary.setScoreReason(scoreReason);
        summary.setAdvice("Fix before merge");
        summary.setComments(Collections.singletonList(draft));
        return summary;
    }

    private ChatCompletionResponse buildResponse(String content, int promptTokens, int completionTokens, int totalTokens) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(totalTokens);
        response.setUsage(usage);
        ChatCompletionResponse.Message message = new ChatCompletionResponse.Message();
        message.setContent(content);
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setMessage(message);
        response.setChoices(Collections.singletonList(choice));
        return response;
    }
}
