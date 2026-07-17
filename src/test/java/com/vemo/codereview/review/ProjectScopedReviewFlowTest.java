package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.service.LlmGatewayService;
import com.vemo.codereview.llm.service.LlmConfigResolverService;
import com.vemo.codereview.notify.service.WeComNotificationService;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.service.GitLabCommentPublisher;
import com.vemo.codereview.platform.gitlab.service.GitLabReviewTargetService;
import com.vemo.codereview.project.service.ProjectConfigService;
import com.vemo.codereview.projecttemplate.service.ProjectTemplateResolverService;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.PromptBuilderService;
import com.vemo.codereview.review.service.ReviewResponseParser;
import com.vemo.codereview.review.service.ReviewResultPersistenceService;
import com.vemo.codereview.review.service.ReviewRetryService;
import com.vemo.codereview.review.service.ReviewRuleService;
import com.vemo.codereview.review.service.ReviewScoreService;
import com.vemo.codereview.review.service.ReviewStateService;
import com.vemo.codereview.review.service.ReviewContextEnrichmentService;
import com.vemo.codereview.review.service.ReviewTaskWorker;
import com.vemo.codereview.review.service.ReviewSemanticUnitPlanner;
import com.vemo.codereview.review.service.ReviewBatchPlanner;
import com.vemo.codereview.review.service.ReviewBatchExecutor;
import com.vemo.codereview.review.service.ReviewBatchAggregator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectScopedReviewFlowTest {

    @Mock
    private ReviewTaskStoreMapper reviewTaskStoreMapper;
    @Mock
    private ReviewEventStoreMapper reviewEventStoreMapper;
    @Mock
    private GitLabReviewTargetService gitLabReviewTargetService;
    @Mock
    private PromptBuilderService promptBuilderService;
    @Mock
    private LlmGatewayService llmGatewayService;
    @Mock
    private ReviewResponseParser reviewResponseParser;
    @Mock
    private ReviewScoreService reviewScoreService;
    @Mock
    private ReviewResultPersistenceService reviewResultPersistenceService;
    @Mock
    private GitLabCommentPublisher gitLabCommentPublisher;
    @Mock
    private ReviewRetryService reviewRetryService;
    @Mock
    private ReviewCommentStoreMapper reviewCommentStoreMapper;
    @Mock
    private WeComNotificationService weComNotificationService;
    @Mock
    private ReviewStateService reviewStateService;
    @Mock
    private ProjectConfigService projectConfigService;
    @Mock
    private ProjectTemplateResolverService projectTemplateResolverService;
    @Mock
    private ReviewRuleService reviewRuleService;
    @Mock
    private ReviewContextEnrichmentService reviewContextEnrichmentService;
    @Mock private LlmConfigResolverService llmConfigResolverService;
    @Mock private ReviewSemanticUnitPlanner reviewSemanticUnitPlanner;
    @Mock private ReviewBatchPlanner reviewBatchPlanner;
    @Mock private ReviewBatchExecutor reviewBatchExecutor;
    @Mock private ReviewBatchAggregator reviewBatchAggregator;

    private ReviewTaskWorker reviewTaskWorker;

    @BeforeEach
    void setUp() {
        when(reviewStateService.claimTaskRunning(anyLong())).thenReturn(true);
        reviewTaskWorker = new ReviewTaskWorker(
            reviewTaskStoreMapper,
            reviewEventStoreMapper,
            gitLabReviewTargetService,
            promptBuilderService,
            llmGatewayService,
            reviewResponseParser,
            reviewScoreService,
            reviewResultPersistenceService,
            gitLabCommentPublisher,
            reviewRetryService,
            reviewCommentStoreMapper,
            weComNotificationService,
            reviewStateService,
            projectConfigService,
            projectTemplateResolverService,
            reviewRuleService,
            reviewContextEnrichmentService,
            new ObjectMapper(),
            llmConfigResolverService,
            reviewSemanticUnitPlanner,
            reviewBatchPlanner,
            reviewBatchExecutor,
            reviewBatchAggregator
        );
    }

    @Test
    void shouldApplyProjectPromptAndSkipPublishingWhenProjectDisablesOutputs() {
        CodeReviewTaskEntity task = buildTask();
        ProjectProfileEntity projectConfig = buildProjectConfig(false, false, null, "check aggregate boundary");
        ReviewPromptPayload reviewPromptPayload = new ReviewPromptPayload();
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("deepseek-chat");
        ReviewSummary reviewSummary = buildReviewSummary();
        CodeReviewResultEntity resultEntity = buildResultEntity(9001L);

        when(reviewTaskStoreMapper.selectById(1L)).thenReturn(task);
        when(projectConfigService.findById(2001L)).thenReturn(projectConfig);
        when(reviewRuleService.getDefaultPromptText()).thenReturn("default prompt");
        when(projectTemplateResolverService.resolveEffectivePrompt(eq(projectConfig), eq("default prompt"))).thenReturn("check aggregate boundary");
        when(projectTemplateResolverService.resolveEffectiveFileExtensions(eq(projectConfig))).thenReturn(null);
        when(gitLabReviewTargetService.getMergeRequestChanges(
            "http://gitlab.example.com/group/subgroup/mas-core",
            1001L,
            "7",
            "project-token"
        )).thenReturn(buildChangesPayload());
        when(promptBuilderService.build(any(ReviewExecutionContext.class))).thenReturn(reviewPromptPayload);
        when(llmGatewayService.review(eq(2001L), eq(reviewPromptPayload))).thenReturn(response);
        when(reviewResponseParser.parse(response)).thenReturn(reviewSummary);
        when(reviewResultPersistenceService.persist(
            eq(1L),
            eq("openai-compatible"),
            eq("deepseek-chat"),
            eq(reviewSummary),
            eq(response),
            any(ReviewExecutionContext.class)))
            .thenReturn(resultEntity);
        when(reviewCommentStoreMapper.selectList(any())).thenReturn(Collections.<CodeReviewCommentEntity>emptyList());

        reviewTaskWorker.process(1L);

        ArgumentCaptor<ReviewExecutionContext> contextCaptor = ArgumentCaptor.forClass(ReviewExecutionContext.class);
        verify(promptBuilderService).build(contextCaptor.capture());
        assertEquals("check aggregate boundary", contextCaptor.getValue().getProjectPromptContent());
        verify(gitLabCommentPublisher, never()).publishMergeRequest(anyLong(), any(), any(), any());
        verify(gitLabCommentPublisher, never()).publishCommit(anyLong(), any(), any(), any());
        verify(weComNotificationService, never()).notifyReviewResult(anyLong(), any(), any(), any(), any());
        verify(reviewStateService).markTaskSuccess(task);
        verify(reviewStateService).markEventProcessed(10L);
    }

    @Test
    void shouldUseProjectWebhookAndPublishWhenProjectEnablesOutputs() {
        CodeReviewTaskEntity task = buildTask();
        ProjectProfileEntity projectConfig = buildProjectConfig(true, true,
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=project-key", null);
        ReviewPromptPayload reviewPromptPayload = new ReviewPromptPayload();
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("deepseek-chat");
        ReviewSummary reviewSummary = buildReviewSummary();
        CodeReviewResultEntity resultEntity = buildResultEntity(9002L);
        CodeReviewCommentEntity commentEntity = new CodeReviewCommentEntity();
        commentEntity.setResultId(9002L);
        commentEntity.setSeverity("HIGH");
        commentEntity.setCategory("Project hard rule");

        when(reviewTaskStoreMapper.selectById(1L)).thenReturn(task);
        when(projectConfigService.findById(2001L)).thenReturn(projectConfig);
        when(reviewRuleService.getDefaultPromptText()).thenReturn("default prompt");
        when(projectTemplateResolverService.resolveEffectivePrompt(eq(projectConfig), eq("default prompt"))).thenReturn(null);
        when(projectTemplateResolverService.resolveEffectiveFileExtensions(eq(projectConfig))).thenReturn(null);
        when(gitLabReviewTargetService.getMergeRequestChanges(
            "http://gitlab.example.com/group/subgroup/mas-core",
            1001L,
            "7",
            "project-token"
        )).thenReturn(buildChangesPayload());
        when(promptBuilderService.build(any(ReviewExecutionContext.class))).thenReturn(reviewPromptPayload);
        when(llmGatewayService.review(eq(2001L), eq(reviewPromptPayload))).thenReturn(response);
        when(reviewResponseParser.parse(response)).thenReturn(reviewSummary);
        when(reviewResultPersistenceService.persist(
            eq(1L),
            eq("openai-compatible"),
            eq("deepseek-chat"),
            eq(reviewSummary),
            eq(response),
            any(ReviewExecutionContext.class)))
            .thenReturn(resultEntity);
        when(reviewCommentStoreMapper.selectList(any())).thenReturn(Arrays.asList(commentEntity));

        reviewTaskWorker.process(1L);

        verify(gitLabCommentPublisher).publishMergeRequest(
            "http://gitlab.example.com/group/subgroup/mas-core",
            1001L,
            "7",
            resultEntity,
            "project-token"
        );
        verify(weComNotificationService).notifyReviewResult(
            eq(2001L),
            any(),
            eq(resultEntity),
            eq(Arrays.asList(commentEntity)),
            eq("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=project-key")
        );
    }

    @Test
    void shouldKeepTaskSuccessfulWhenEventProcessedMarkingFails() {
        CodeReviewTaskEntity task = buildTask();
        ProjectProfileEntity projectConfig = buildProjectConfig(false, false, null, null);
        ReviewPromptPayload reviewPromptPayload = new ReviewPromptPayload();
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("deepseek-chat");
        ReviewSummary reviewSummary = buildReviewSummary();
        CodeReviewResultEntity resultEntity = buildResultEntity(9003L);

        when(reviewTaskStoreMapper.selectById(1L)).thenReturn(task);
        when(projectConfigService.findById(2001L)).thenReturn(projectConfig);
        when(reviewRuleService.getDefaultPromptText()).thenReturn("default prompt");
        when(projectTemplateResolverService.resolveEffectivePrompt(eq(projectConfig), eq("default prompt"))).thenReturn(null);
        when(projectTemplateResolverService.resolveEffectiveFileExtensions(eq(projectConfig))).thenReturn(null);
        when(gitLabReviewTargetService.getMergeRequestChanges(
            "http://gitlab.example.com/group/subgroup/mas-core",
            1001L,
            "7",
            "project-token"
        )).thenReturn(buildChangesPayload());
        when(promptBuilderService.build(any(ReviewExecutionContext.class))).thenReturn(reviewPromptPayload);
        when(llmGatewayService.review(eq(2001L), eq(reviewPromptPayload))).thenReturn(response);
        when(reviewResponseParser.parse(response)).thenReturn(reviewSummary);
        when(reviewResultPersistenceService.persist(
            eq(1L),
            eq("openai-compatible"),
            eq("deepseek-chat"),
            eq(reviewSummary),
            eq(response),
            any(ReviewExecutionContext.class)))
            .thenReturn(resultEntity);
        when(reviewCommentStoreMapper.selectList(any())).thenReturn(Collections.<CodeReviewCommentEntity>emptyList());
        doThrow(new RuntimeException("event state mismatch")).when(reviewStateService).markEventProcessed(10L);

        assertDoesNotThrow(() -> reviewTaskWorker.process(1L));

        verify(reviewStateService).markTaskSuccess(task);
        verify(reviewStateService).markEventProcessed(10L);
        verify(reviewRetryService, never()).handleFailure(eq(task), any(RuntimeException.class));
    }

    @Test
    void shouldFetchPushRangeInsteadOfSingleCommitDiff() {
        CodeReviewTaskEntity task = buildTask();
        task.setTaskType("PUSH_REVIEW");
        task.setTargetId("head-c");
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setId(10L);
        event.setObjectType("push");
        event.setSubmitBranch("test-cr");
        event.setPayloadJson("{\"object_kind\":\"push\",\"before\":\"base-a\",\"after\":\"head-c\","
            + "\"total_commits_count\":3,\"commits\":[{\"id\":\"head-c\"}]}");
        ProjectProfileEntity projectConfig = buildProjectConfig(false, false, null, null);
        ReviewPromptPayload prompt = new ReviewPromptPayload();
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setModel("deepseek-chat");
        ReviewSummary summary = buildReviewSummary();

        when(reviewTaskStoreMapper.selectById(1L)).thenReturn(task);
        when(reviewEventStoreMapper.selectById(10L)).thenReturn(event);
        when(projectConfigService.findById(2001L)).thenReturn(projectConfig);
        when(reviewRuleService.getDefaultPromptText()).thenReturn("default prompt");
        when(projectTemplateResolverService.resolveEffectivePrompt(eq(projectConfig), eq("default prompt"))).thenReturn(null);
        when(projectTemplateResolverService.resolveEffectiveFileExtensions(eq(projectConfig))).thenReturn(null);
        when(gitLabReviewTargetService.getPushChanges(
            eq("http://gitlab.example.com/group/subgroup/mas-core"), eq(1001L), eq("base-a"), eq("head-c"), eq("test-cr"),
            any(), eq("project-token"))).thenReturn(buildChangesPayload());
        when(promptBuilderService.build(any(ReviewExecutionContext.class))).thenReturn(prompt);
        when(llmGatewayService.review(2001L, prompt)).thenReturn(response);
        when(reviewResponseParser.parse(response)).thenReturn(summary);
        when(reviewResultPersistenceService.persist(eq(1L), eq("openai-compatible"), eq("deepseek-chat"),
            eq(summary), eq(response), any(ReviewExecutionContext.class))).thenReturn(buildResultEntity(9004L));
        when(reviewCommentStoreMapper.selectList(any())).thenReturn(Collections.<CodeReviewCommentEntity>emptyList());

        ReviewExecutionContext context = reviewTaskWorker.process(1L);

        assertEquals("push", context.getTargetType());
        assertEquals("base-a", context.getBeforeSha());
        assertEquals("head-c", context.getAfterSha());
        assertEquals(Integer.valueOf(3), context.getCommitCount());
        verify(gitLabReviewTargetService, never()).getCommitChanges(any(), anyLong(), any(), any(), any());
    }

    private CodeReviewTaskEntity buildTask() {
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setId(1L);
        task.setEventId(10L);
        task.setTaskType("MR_REVIEW");
        task.setProjectId(2001L);
        task.setTargetId("7");
        task.setTargetTitle("Add review pipeline");
        task.setStatus("PENDING");
        return task;
    }

    private ProjectProfileEntity buildProjectConfig(boolean gitlabNoteEnabled, boolean wecomNotifyEnabled,
                                                    String wecomWebhookUrl, String promptContent) {
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setId(2001L);
        entity.setGitlabProjectId(1001L);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/subgroup/mas-core");
        entity.setGitlabWebhookToken("project-token");
        entity.setGitlabNoteEnabled(gitlabNoteEnabled);
        entity.setWecomNotifyEnabled(wecomNotifyEnabled);
        entity.setWecomWebhookUrl(wecomWebhookUrl);
        entity.setPromptContent(promptContent);
        entity.setActive(true);
        entity.setAiReviewEnabled(true);
        return entity;
    }

    private GitLabChangesPayload buildChangesPayload() {
        GitLabChangesPayload payload = new GitLabChangesPayload();
        payload.setChanges(Collections.<GitLabChangesPayload.Change>emptyList());
        return payload;
    }

    private ReviewSummary buildReviewSummary() {
        ReviewSummary summary = new ReviewSummary();
        summary.setSuggestedScore(88);
        summary.setDeductionScore(12);
        summary.setFinalScore(76);
        summary.setRiskLevel("HIGH");
        summary.setSummary("Found high-risk issues");
        summary.setBriefSummary("Found high-risk issues");
        summary.setComments(Arrays.asList(buildCommentDraft()));
        return summary;
    }

    private ReviewCommentDraft buildCommentDraft() {
        ReviewCommentDraft draft = new ReviewCommentDraft();
        draft.setFilePath("src/main/java/com/vemo/App.java");
        draft.setLine(42);
        draft.setSeverity("HIGH");
        draft.setCategory("Project hard rule");
        draft.setMessage("Problem");
        draft.setSuggestion("Suggestion");
        draft.setCommentHash("hash");
        return draft;
    }

    private CodeReviewResultEntity buildResultEntity(Long id) {
        CodeReviewResultEntity entity = new CodeReviewResultEntity();
        entity.setId(id);
        entity.setSummary("Found high-risk issues");
        entity.setBriefSummary("Found high-risk issues");
        entity.setRiskLevel("HIGH");
        return entity;
    }
}
