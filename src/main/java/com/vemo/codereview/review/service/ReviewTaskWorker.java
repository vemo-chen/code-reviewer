package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.service.LlmGatewayService;
import com.vemo.codereview.llm.service.LlmConfigResolverService;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.notify.model.ReviewNotificationMetadata;
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
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFixStatus;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewBatchOutput;
import com.vemo.codereview.review.model.AggregatedReviewOutput;
import com.vemo.codereview.review.model.MrReviewCompletion;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReviewTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskWorker.class);

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    @Autowired
    private ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewEventStoreMapper reviewEventStoreMapper;
    private final GitLabReviewTargetService gitLabReviewTargetService;
    private final PromptBuilderService promptBuilderService;
    private final LlmGatewayService llmGatewayService;
    private final ReviewResponseParser reviewResultParser;
    private final ReviewScoreService reviewScoreService;
    private final ReviewResultPersistenceService reviewResultPersistenceService;
    private final GitLabCommentPublisher gitLabCommentPublisher;
    private final ReviewRetryService reviewRetryService;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;
    private final WeComNotificationService weComNotificationService;
    private final ReviewStateService reviewStateService;
    private final ProjectConfigService projectConfigService;
    private final ProjectTemplateResolverService projectTemplateResolverService;
    private final ReviewRuleService reviewRuleService;
    private final ReviewContextEnrichmentService reviewContextEnrichmentService;
    private final ObjectMapper objectMapper;
    private final LlmConfigResolverService llmConfigResolverService;
    private final ReviewSemanticUnitPlanner reviewSemanticUnitPlanner;
    private final ReviewBatchPlanner reviewBatchPlanner;
    private final ReviewBatchExecutor reviewBatchExecutor;
    private final ReviewBatchAggregator reviewBatchAggregator;

    public ReviewTaskWorker(
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewEventStoreMapper reviewEventStoreMapper,
        GitLabReviewTargetService gitLabReviewTargetService,
        PromptBuilderService promptBuilderService,
        LlmGatewayService llmGatewayService,
        ReviewResponseParser reviewResultParser,
        ReviewScoreService reviewScoreService,
        ReviewResultPersistenceService reviewResultPersistenceService,
        GitLabCommentPublisher gitLabCommentPublisher,
        ReviewRetryService reviewRetryService,
        ReviewCommentStoreMapper codeReviewCommentMapper,
        WeComNotificationService weComNotificationService,
        ReviewStateService reviewStateService,
        ProjectConfigService projectConfigService,
        ProjectTemplateResolverService projectTemplateResolverService,
        ReviewRuleService reviewRuleService,
        ReviewContextEnrichmentService reviewContextEnrichmentService,
        ObjectMapper objectMapper,
        LlmConfigResolverService llmConfigResolverService,
        ReviewSemanticUnitPlanner reviewSemanticUnitPlanner,
        ReviewBatchPlanner reviewBatchPlanner,
        ReviewBatchExecutor reviewBatchExecutor,
        ReviewBatchAggregator reviewBatchAggregator) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.reviewEventStoreMapper = reviewEventStoreMapper;
        this.gitLabReviewTargetService = gitLabReviewTargetService;
        this.promptBuilderService = promptBuilderService;
        this.llmGatewayService = llmGatewayService;
        this.reviewResultParser = reviewResultParser;
        this.reviewScoreService = reviewScoreService;
        this.reviewResultPersistenceService = reviewResultPersistenceService;
        this.gitLabCommentPublisher = gitLabCommentPublisher;
        this.reviewRetryService = reviewRetryService;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
        this.weComNotificationService = weComNotificationService;
        this.reviewStateService = reviewStateService;
        this.projectConfigService = projectConfigService;
        this.projectTemplateResolverService = projectTemplateResolverService;
        this.reviewRuleService = reviewRuleService;
        this.reviewContextEnrichmentService = reviewContextEnrichmentService;
        this.objectMapper = objectMapper;
        this.llmConfigResolverService = llmConfigResolverService;
        this.reviewSemanticUnitPlanner = reviewSemanticUnitPlanner;
        this.reviewBatchPlanner = reviewBatchPlanner;
        this.reviewBatchExecutor = reviewBatchExecutor;
        this.reviewBatchAggregator = reviewBatchAggregator;
    }

    public ReviewExecutionContext process(Long taskId) {
        return process(taskId, true, UUID.randomUUID().toString());
    }

    public ReviewExecutionContext processClaimed(Long taskId) {
        return process(taskId, false, null);
    }

    public ReviewExecutionContext processClaimed(Long taskId, String executionToken) {
        return process(taskId, false, executionToken);
    }

    private ReviewExecutionContext process(Long taskId, boolean claimBeforeStart, String executionToken) {
        long workerStartNs = System.nanoTime();
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        log.info("review task loaded. taskId={}, taskType={}, status={}, elapsedMs={}",
            task.getId(), task.getTaskType(), task.getStatus(), elapsedMs(workerStartNs));
        if (claimBeforeStart) {
            if (!canStart(task.getStatus())) {
                log.info("review task skipped because it is no longer runnable. taskId={}, status={}",
                    task.getId(), task.getStatus());
                return null;
            }

            if (!reviewStateService.claimTaskRunning(task.getId(), executionToken)) {
                log.info("review task claim skipped because another worker owns it. taskId={}, status={}",
                    task.getId(), task.getStatus());
                return null;
            }
            task.setStatus(ReviewTaskLifecycle.RUNNING.name());
            task.setStartedAt(new Date());
            task.setExecutionToken(executionToken);
            log.info("review task marked running. taskId={}, elapsedMs={}", task.getId(), elapsedMs(workerStartNs));
        } else if (!ReviewTaskLifecycle.RUNNING.name().equals(task.getStatus())) {
            log.info("review task skipped because claimed task is no longer running. taskId={}, status={}",
                task.getId(), task.getStatus());
            return null;
        } else if (executionToken != null && !executionToken.equals(task.getExecutionToken())) {
            log.info("review task skipped because execution token changed. taskId={}", task.getId());
            return null;
        }

        CodeReviewEventEntity startingEvent = task.getEventId() == null ? null
            : reviewEventStoreMapper.selectById(task.getEventId());
        String expectedMrHeadSha = startingEvent == null ? null : startingEvent.getMrHeadSha();
        boolean completedAtomically = false;
        boolean markEventProcessedAfterCompletion = false;
        ReviewExecutionContext context;
        try {
            ProjectProfileEntity projectConfig = projectConfigService.findById(task.getProjectId());
            log.info("review project config loaded. taskId={}, projectId={}, elapsedMs={}",
                task.getId(), task.getProjectId(), elapsedMs(workerStartNs));
            Long gitLabProjectId = resolveGitLabProjectId(task, projectConfig);
            String gitLabProjectUrl = resolveGitLabProjectUrl(task, projectConfig);
            String gitLabApiToken = resolveGitLabApiToken(projectConfig);
            long contextStartNs = System.nanoTime();
            context = buildReviewContext(task, projectConfig, gitLabProjectId, gitLabProjectUrl, gitLabApiToken);
            log.info("review context built. taskId={}, targetType={}, gitlabFetchAndContextMs={}, elapsedMs={}",
                task.getId(), context.getTargetType(), elapsedMs(contextStartNs), elapsedMs(workerStartNs));
            LlmRuntimeConfig runtimeConfig = llmConfigResolverService.resolve(task.getProjectId());
            CodeReviewResultEntity persistedResult;
            if (runtimeConfig == null) {
                ReviewPromptPayload reviewPrompt = promptBuilderService.build(context);
                ChatCompletionResponse llmResponse = llmGatewayService.review(task.getProjectId(), reviewPrompt);
                ReviewSummary reviewSummary = reviewResultParser.parse(llmResponse);
                reviewScoreService.applyScores(reviewSummary);
                if ("MR_REVIEW".equals(task.getTaskType())) {
                    MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
                        task.getId(), expectedMrHeadSha, executionToken, "openai-compatible",
                        llmResponse.getModel(), reviewSummary, llmResponse, context);
                    if (!completion.isCompleted()) {
                        log.info("review task result stale. taskId={}, expectedHead={}", task.getId(), expectedMrHeadSha);
                        return context;
                    }
                    completedAtomically = true;
                    persistedResult = completion.getResult();
                } else {
                    persistedResult = reviewResultPersistenceService.completeReview(task.getId(), executionToken, "openai-compatible",
                        llmResponse.getModel(), reviewSummary, llmResponse, context);
                    if (persistedResult == null) {
                        log.info("review task result ignored because execution token is no longer current. taskId={}",
                            task.getId());
                        return context;
                    }
                    completedAtomically = true;
                    markEventProcessedAfterCompletion = true;
                }
            } else {
                List<ReviewSemanticUnit> units = reviewSemanticUnitPlanner.plan(context,
                    projectConfig == null || !Boolean.FALSE.equals(projectConfig.getReviewContextEnabled()));
                List<ReviewExecutionBatch> batches = reviewBatchPlanner.plan(units, runtimeConfig.getMaxTokens());
                AggregatedReviewOutput aggregate;
                if (batches.isEmpty()) {
                    aggregate = emptyAggregate(runtimeConfig);
                } else {
                    List<ReviewBatchOutput> outputs = reviewBatchExecutor.execute(
                        task.getId(), task.getProjectId(), context, batches, runtimeConfig);
                    aggregate = reviewBatchAggregator.aggregate(outputs);
                }
                if ("MR_REVIEW".equals(task.getTaskType())) {
                    MrReviewCompletion completion = reviewResultPersistenceService.completeMrReview(
                        task.getId(), expectedMrHeadSha, executionToken, aggregate, context);
                    if (!completion.isCompleted()) {
                        log.info("review task result stale. taskId={}, expectedHead={}", task.getId(), expectedMrHeadSha);
                        return context;
                    }
                    completedAtomically = true;
                    persistedResult = completion.getResult();
                } else {
                    persistedResult = reviewResultPersistenceService.completeAggregatedReview(
                        task.getId(), executionToken, aggregate, context);
                    if (persistedResult == null) {
                        log.info("review task result ignored because execution token is no longer current. taskId={}",
                            task.getId());
                        return context;
                    }
                    completedAtomically = true;
                    markEventProcessedAfterCompletion = true;
                }
            }
            log.info("review result persisted. taskId={}, resultId={}, elapsedMs={}",
                task.getId(), persistedResult.getId(), elapsedMs(workerStartNs));
            if (shouldPublishGitLab(projectConfig)) {
                long publishStartNs = System.nanoTime();
                publishReviewResult(task, persistedResult, context, gitLabProjectId, gitLabProjectUrl, gitLabApiToken);
                log.info("review gitlab note published. taskId={}, publishMs={}, elapsedMs={}",
                    task.getId(), elapsedMs(publishStartNs), elapsedMs(workerStartNs));
            }

            QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
            commentWrapper.eq("result_id", persistedResult.getId());
            List<CodeReviewCommentEntity> comments = codeReviewCommentMapper.selectList(commentWrapper);
            if (shouldNotifyWeCom(projectConfig)) {
                markWeComPending(persistedResult);
                try {
                    weComNotificationService.notifyReviewResult(
                        task.getProjectId(), buildNotificationMetadata(task, context, projectConfig), persistedResult, comments,
                        projectConfig.getWecomWebhookUrl());
                    markWeComSuccess(persistedResult);
                } catch (RuntimeException ex) {
                    markWeComFailure(persistedResult, ex);
                    log.warn("WeCom notification failed without changing review task status. taskId={}, resultId={}, message={}",
                        task.getId(), persistedResult.getId(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("review task failed. taskId={}, elapsedMs={}, message={}",
                task.getId(), elapsedMs(workerStartNs), ex.getMessage());
            reviewRetryService.handleFailure(task, executionToken, ex);
            throw ex;
        }

        if (completedAtomically) {
            if (markEventProcessedAfterCompletion) {
                markEventProcessedSafely(task.getEventId());
            }
            log.info("review task finished. taskId={}, totalMs={}", task.getId(), elapsedMs(workerStartNs));
            return context;
        }
        task.setFixStatus(ReviewFixStatus.TO_BE_FIXED.name());
        task.setFixSubmittedBy(null);
        task.setFixSubmittedAt(null);
        task.setFixReviewedBy(null);
        task.setFixReviewedAt(null);
        task.setFixReviewComment(null);
        reviewStateService.markTaskSuccess(task);
        markEventProcessedSafely(task.getEventId());
        log.info("review task finished. taskId={}, totalMs={}", task.getId(), elapsedMs(workerStartNs));
        return context;
    }

    private AggregatedReviewOutput emptyAggregate(LlmRuntimeConfig runtimeConfig) {
        ReviewSummary summary = new ReviewSummary();
        summary.setRiskLevel("LOW");
        summary.setSummary("本次变更未包含符合审查范围的代码文件");
        summary.setBriefSummary("本次变更未包含符合审查范围的代码文件");
        summary.setComments(Collections.<com.vemo.codereview.review.model.ReviewCommentDraft>emptyList());
        AggregatedReviewOutput output = new AggregatedReviewOutput();
        output.setSummary(summary);
        output.setProviderName("openai-compatible");
        output.setModelName(runtimeConfig.getModelName());
        output.setInputTokens(0);
        output.setOutputTokens(0);
        output.setLatencyMs(0L);
        output.setRawResponse("[]");
        return output;
    }

    private void markEventProcessedSafely(Long eventId) {
        if (eventId == null) {
            return;
        }
        try {
            reviewStateService.markEventProcessed(eventId);
        } catch (RuntimeException ex) {
            log.warn("Failed to mark review event as processed, eventId={}, message={}", eventId, ex.getMessage());
        }
    }

    private boolean canStart(String status) {
        return ReviewTaskLifecycle.PENDING.name().equals(status)
            || ReviewTaskLifecycle.FAILED.name().equals(status);
    }

    private ReviewExecutionContext buildReviewContext(
        CodeReviewTaskEntity task,
        ProjectProfileEntity projectConfig,
        Long gitLabProjectId,
        String gitLabProjectUrl,
        String gitLabApiToken) {
        ReviewExecutionContext context = new ReviewExecutionContext();
        context.setTaskId(task.getId());
        context.setEventId(task.getEventId());
        context.setProjectId(task.getProjectId());
        context.setProjectPromptContent(
            projectTemplateResolverService.resolveEffectivePrompt(projectConfig, reviewRuleService.getDefaultPromptText())
        );
        context.setSupportedFileExtensions(
            projectTemplateResolverService.resolveEffectiveFileExtensions(projectConfig)
        );
        context.setTargetId(task.getTargetId());
        context.setTargetTitle(task.getTargetTitle());

        GitLabChangesPayload changes;
        if ("MR_REVIEW".equals(task.getTaskType())) {
            context.setTargetType("merge_request");
            changes = gitLabReviewTargetService.getMergeRequestChanges(
                gitLabProjectUrl,
                gitLabProjectId,
                task.getTargetId(),
                gitLabApiToken
            );
        } else if ("PUSH_REVIEW".equals(task.getTaskType())) {
            CodeReviewEventEntity event = task.getEventId() == null ? null
                : reviewEventStoreMapper.selectById(task.getEventId());
            if (event != null && "push".equalsIgnoreCase(event.getObjectType())) {
                GitLabWebhookPayload push = readPushPayload(event);
                context.setTargetType("push");
                context.setPushBranch(event.getSubmitBranch());
                context.setBeforeSha(push.getBefore());
                context.setAfterSha(push.getAfter());
                context.setCommitCount(push.getTotalCommitsCount() == null
                    ? (push.getCommits() == null ? 0 : push.getCommits().size()) : push.getTotalCommitsCount());
                context.setSourceRef(push.getAfter());
                context.setTargetRef(push.getBefore());
                changes = gitLabReviewTargetService.getPushChanges(
                    gitLabProjectUrl, gitLabProjectId, push.getBefore(), push.getAfter(), event.getSubmitBranch(),
                    push, gitLabApiToken);
            } else {
                context.setTargetType("commit");
                changes = gitLabReviewTargetService.getCommitChanges(
                    gitLabProjectUrl, gitLabProjectId, task.getTargetId(), task.getTargetTitle(), gitLabApiToken);
            }
        } else {
            throw new DomainException("TASK_TYPE_UNSUPPORTED", "Unsupported review task type: " + task.getTaskType());
        }
        context.setMergeRequestChanges(changes);
        reviewContextEnrichmentService.enrich(context, projectConfig, gitLabProjectId, gitLabProjectUrl, gitLabApiToken);
        return context;
    }

    private GitLabWebhookPayload readPushPayload(CodeReviewEventEntity event) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), GitLabWebhookPayload.class);
        } catch (Exception ex) {
            throw new DomainException("PUSH_PAYLOAD_INVALID", "Failed to parse push webhook payload");
        }
    }

    private ReviewNotificationMetadata buildNotificationMetadata(CodeReviewTaskEntity task,
                                                                 ReviewExecutionContext context,
                                                                 ProjectProfileEntity projectConfig) {
        ReviewNotificationMetadata metadata = new ReviewNotificationMetadata();
        metadata.setReviewTargetType(task.getTaskType());
        metadata.setTargetId(task.getTargetId());
        metadata.setSubmitMessage(task.getTargetTitle());
        if (projectConfig != null) {
            metadata.setGitlabUrl(buildGitLabTargetUrl(projectConfig.getGitlabProjectUrl(), task, context));
        }
        if (context != null && "push".equals(context.getTargetType())) {
            metadata.setPushBranch(context.getPushBranch());
            metadata.setBeforeSha(context.getBeforeSha());
            metadata.setAfterSha(context.getAfterSha());
            metadata.setCommitCount(context.getCommitCount());
        }

        if (task.getEventId() == null) {
            return metadata;
        }
        CodeReviewEventEntity event = reviewEventStoreMapper.selectById(task.getEventId());
        if (event == null) {
            return metadata;
        }
        metadata.setSubmitter(event.getOperatorName());
        metadata.setSubmitBranch(event.getSubmitBranch());
        metadata.setSubmitTime(resolveSubmitTime(null, task, event));
        if (!StringUtils.hasText(event.getPayloadJson())) {
            return metadata;
        }
        try {
            GitLabWebhookPayload payload = objectMapper.readValue(event.getPayloadJson(), GitLabWebhookPayload.class);
            metadata.setSubmitter(resolveSubmitter(payload, event));
            metadata.setSubmitBranch(resolveBranch(payload));
            metadata.setSubmitTime(resolveSubmitTime(payload, task, event));
            String submitMessage = resolveSubmitMessage(payload, task);
            if (StringUtils.hasText(submitMessage)) {
                metadata.setSubmitMessage(submitMessage);
            }
        } catch (Exception ignored) {
            metadata.setSubmitter(event.getOperatorName());
        }
        return metadata;
    }

    private String buildGitLabTargetUrl(String projectUrl, CodeReviewTaskEntity task,
                                        ReviewExecutionContext context) {
        if (!StringUtils.hasText(projectUrl) || task == null) return null;
        String base = projectUrl.trim().replaceAll("/+$", "");
        if ("MR_REVIEW".equalsIgnoreCase(task.getTaskType())) {
            return base + "/merge_requests/" + task.getTargetId();
        }
        String sha = context != null && StringUtils.hasText(context.getAfterSha())
            ? context.getAfterSha() : task.getTargetId();
        return StringUtils.hasText(sha) ? base + "/commit/" + sha : null;
    }

    private String resolveSubmitter(GitLabWebhookPayload payload, CodeReviewEventEntity event) {
        if (payload == null) {
            return event == null ? null : event.getOperatorName();
        }
        if (payload.getUser() != null && StringUtils.hasText(payload.getUser().getName())) {
            return payload.getUser().getName();
        }
        if (StringUtils.hasText(payload.getUserName())) {
            return payload.getUserName();
        }
        return event == null ? null : event.getOperatorName();
    }

    private String resolveBranch(GitLabWebhookPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload.getObjectAttributes() != null && StringUtils.hasText(payload.getObjectAttributes().getSourceBranch())) {
            return payload.getObjectAttributes().getSourceBranch();
        }
        if (StringUtils.hasText(payload.getRef())) {
            String ref = payload.getRef();
            int index = ref.lastIndexOf('/');
            return index >= 0 ? ref.substring(index + 1) : ref;
        }
        return null;
    }

    private String resolveSubmitTime(GitLabWebhookPayload payload, CodeReviewTaskEntity task, CodeReviewEventEntity event) {
        String raw = null;
        if (payload != null && payload.getCommits() != null && !payload.getCommits().isEmpty()) {
            GitLabWebhookPayload.Commit latest = payload.getCommits().get(payload.getCommits().size() - 1);
            raw = latest == null ? null : latest.getTimestamp();
        }
        if (!StringUtils.hasText(raw) && event != null && event.getSubmitTime() != null) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return formatter.format(event.getSubmitTime());
        }
        if (!StringUtils.hasText(raw) && task != null && task.getCreatedAt() != null) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(task.getCreatedAt());
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            Date parsed = parser.parse(raw);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return formatter.format(parsed);
        } catch (ParseException ex) {
            return raw;
        }
    }

    private String resolveSubmitMessage(GitLabWebhookPayload payload, CodeReviewTaskEntity task) {
        if (payload != null && payload.getCommits() != null && !payload.getCommits().isEmpty()) {
            GitLabWebhookPayload.Commit latest = payload.getCommits().get(payload.getCommits().size() - 1);
            if (latest != null) {
                if (StringUtils.hasText(latest.getTitle())) {
                    return latest.getTitle().trim();
                }
                if (StringUtils.hasText(latest.getMessage())) {
                    return latest.getMessage().trim();
                }
            }
        }
        return task == null ? null : task.getTargetTitle();
    }

    private void publishReviewResult(
        CodeReviewTaskEntity task,
        CodeReviewResultEntity persistedResult,
        ReviewExecutionContext context,
        Long gitLabProjectId,
        String gitLabProjectUrl,
        String gitLabApiToken) {
        if ("MR_REVIEW".equals(task.getTaskType())) {
            gitLabCommentPublisher.publishMergeRequest(
                gitLabProjectUrl,
                gitLabProjectId,
                task.getTargetId(),
                persistedResult,
                gitLabApiToken
            );
            return;
        }
        if ("PUSH_REVIEW".equals(task.getTaskType())) {
            if (context != null && "push".equals(context.getTargetType())) {
                gitLabCommentPublisher.publishPushRange(
                    gitLabProjectUrl, gitLabProjectId, context, persistedResult, gitLabApiToken);
                return;
            }
            gitLabCommentPublisher.publishCommit(
                gitLabProjectUrl,
                gitLabProjectId,
                task.getTargetId(),
                persistedResult,
                gitLabApiToken
            );
            return;
        }
        throw new DomainException("TASK_TYPE_UNSUPPORTED", "Unsupported review task type: " + task.getTaskType());
    }

    private String resolveGitLabApiToken(ProjectProfileEntity projectConfig) {
        if (projectConfig == null || !StringUtils.hasText(projectConfig.getGitlabWebhookToken())) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required for project review");
        }
        return projectConfig.getGitlabWebhookToken().trim();
    }

    private Long resolveGitLabProjectId(CodeReviewTaskEntity task, ProjectProfileEntity projectConfig) {
        if (projectConfig == null || projectConfig.getGitlabProjectId() == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project configuration not found for task: " + task.getId());
        }
        return projectConfig.getGitlabProjectId();
    }

    private String resolveGitLabProjectUrl(CodeReviewTaskEntity task, ProjectProfileEntity projectConfig) {
        if (projectConfig == null || !StringUtils.hasText(projectConfig.getGitlabProjectUrl())) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required for task: " + task.getId());
        }
        return projectConfig.getGitlabProjectUrl().trim();
    }

    private boolean shouldPublishGitLab(ProjectProfileEntity projectConfig) {
        return projectConfig == null || Boolean.TRUE.equals(projectConfig.getGitlabNoteEnabled());
    }

    private boolean shouldNotifyWeCom(ProjectProfileEntity projectConfig) {
        return projectConfig != null && Boolean.TRUE.equals(projectConfig.getWecomNotifyEnabled());
    }

    private void markWeComPending(CodeReviewResultEntity result) {
        if (codeReviewResultMapper == null) return;
        try {
            result.setWecomNotifyStatus("PENDING");
            result.setWecomNotifyAttempts((result.getWecomNotifyAttempts() == null ? 0 : result.getWecomNotifyAttempts()) + 1);
            result.setWecomNotifyErrorCode(null);
            result.setWecomNotifyErrorMessage(null);
            codeReviewResultMapper.updateById(result);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist WeCom pending status. resultId={}, message={}", result.getId(), ex.getMessage());
        }
    }

    private void markWeComSuccess(CodeReviewResultEntity result) {
        if (codeReviewResultMapper == null) return;
        try {
            result.setWecomNotifyStatus("SUCCESS");
            result.setWecomNotifiedAt(new Date());
            codeReviewResultMapper.updateById(result);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist WeCom success status. resultId={}, message={}", result.getId(), ex.getMessage());
        }
    }

    private void markWeComFailure(CodeReviewResultEntity result, RuntimeException ex) {
        if (codeReviewResultMapper == null) return;
        try {
            result.setWecomNotifyStatus("FAILED");
            result.setWecomNotifyErrorCode(ex instanceof DomainException
                ? ((DomainException) ex).getCode() : "WECOM_PUSH_ERROR");
            result.setWecomNotifyErrorMessage(ex.getMessage());
            codeReviewResultMapper.updateById(result);
        } catch (RuntimeException statusEx) {
            log.warn("Failed to persist WeCom failure status. resultId={}, message={}",
                result.getId(), statusEx.getMessage());
        }
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
