package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.AggregatedReviewOutput;
import com.vemo.codereview.review.model.MrReviewCompletion;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.ReviewResultPersistenceService;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@ExtendWith(OutputCaptureExtension.class)
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

    @Autowired
    private ReviewEventStoreMapper eventMapper;

    @Autowired
    private ReviewTaskStoreMapper taskMapper;

    @Test
    void shouldCompleteMrReviewWithFixStatusAtomically() {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(3001L);
        event.setProjectName("MR atomic completion");
        event.setObjectId("mr-atomic-1");
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("head-1");
        event.setIdempotentKey("mr-atomic-fix-status-1");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(3001L);
        task.setTargetId("31");
        task.setTargetTitle("Atomic MR completion");
        task.setStatus("RUNNING");
        task.setExecutionToken("mr-token-1");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReviewSummary summary = new ReviewSummary();
        summary.setRiskLevel("LOW");
        summary.setSummary("No blocking issue");
        summary.setComments(Collections.<ReviewCommentDraft>emptyList());
        AggregatedReviewOutput output = new AggregatedReviewOutput();
        output.setSummary(summary);
        output.setProviderName("openai-compatible");
        output.setModelName("deepseek-chat");

        MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
            task.getId(), "head-1", "mr-token-1", output, null);
        CodeReviewTaskEntity savedTask = taskMapper.selectById(task.getId());

        assertTrue(completion.isCompleted());
        assertEquals("SUCCESS", savedTask.getStatus());
        assertEquals("TO_BE_FIXED", savedTask.getFixStatus());
        assertEquals(null, savedTask.getExecutionToken());
    }

    @Test
    void shouldNotPersistChatMrReviewWhenHeadChanges() {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(3001L);
        event.setProjectName("MR stale completion");
        event.setObjectId("mr-stale-1");
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("head-2");
        event.setIdempotentKey("mr-stale-chat-completion-1");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(3001L);
        task.setTargetId("32");
        task.setTargetTitle("Stale MR completion");
        task.setStatus("RUNNING");
        task.setExecutionToken("mr-token-2");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReviewSummary summary = new ReviewSummary();
        summary.setRiskLevel("LOW");
        summary.setSummary("No blocking issue");
        summary.setComments(Collections.<ReviewCommentDraft>emptyList());

        MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
            task.getId(), "head-1", "mr-token-2", "openai-compatible", "deepseek-chat", summary,
            buildResponse("{\"summary\":\"No blocking issue\"}", 120, 35, 155), null);

        Long resultCount = codeReviewResultMapper.selectCount(
            new QueryWrapper<CodeReviewResultEntity>().eq("task_id", task.getId()));

        assertFalse(completion.isCompleted());
        assertEquals(Long.valueOf(0L), resultCount);
    }

    @Test
    void shouldNotPersistMrReviewWhenExecutionTokenChanges() {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(3001L);
        event.setProjectName("MR interrupted completion");
        event.setObjectId("mr-interrupted-1");
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("head-3");
        event.setIdempotentKey("mr-interrupted-token-completion-1");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(3001L);
        task.setTargetId("33");
        task.setTargetTitle("Interrupted MR completion");
        task.setStatus("FAILED");
        task.setExecutionToken(null);
        task.setRetryCount(0);
        task.setErrorCode("USER_INTERRUPTED");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReviewSummary summary = new ReviewSummary();
        summary.setRiskLevel("LOW");
        summary.setSummary("No blocking issue");
        summary.setComments(Collections.<ReviewCommentDraft>emptyList());

        MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
            task.getId(), "head-3", "old-token", "openai-compatible", "deepseek-chat", summary,
            buildResponse("{\"summary\":\"No blocking issue\"}", 120, 35, 155), null);

        Long resultCount = codeReviewResultMapper.selectCount(
            new QueryWrapper<CodeReviewResultEntity>().eq("task_id", task.getId()));
        CodeReviewTaskEntity savedTask = taskMapper.selectById(task.getId());

        assertFalse(completion.isCompleted());
        assertEquals(Long.valueOf(0L), resultCount);
        assertEquals("FAILED", savedTask.getStatus());
        assertEquals("USER_INTERRUPTED", savedTask.getErrorCode());
    }

    @Test
    void shouldLogDiscardedMrReviewWhenExecutionTokenDoesNotMatch(CapturedOutput output) {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(3001L);
        event.setProjectName("MR token mismatch log");
        event.setObjectId("mr-token-log-1");
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("head-log");
        event.setIdempotentKey("mr-token-mismatch-log-1");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(3001L);
        task.setTargetId("34");
        task.setTargetTitle("Token mismatch MR completion");
        task.setStatus("RUNNING");
        task.setExecutionToken("current-token");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReviewSummary summary = new ReviewSummary();
        summary.setRiskLevel("LOW");
        summary.setSummary("No blocking issue");
        summary.setComments(Collections.<ReviewCommentDraft>emptyList());

        MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
            task.getId(), "head-log", "old-token", "openai-compatible", "deepseek-chat", summary,
            buildResponse("{\"summary\":\"No blocking issue\"}", 120, 35, 155), null);

        assertFalse(completion.isCompleted());
        assertTrue(output.toString().contains("review task result discarded before persistence"));
        assertTrue(output.toString().contains("reason=EXECUTION_TOKEN_MISMATCH"));
        assertTrue(output.toString().contains("taskId=" + task.getId()));
    }

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
