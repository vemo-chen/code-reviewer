package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.service.LlmGatewayService;
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
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFixStatus;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskWorker.class);

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
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
    private final ObjectMapper objectMapper;

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
        ObjectMapper objectMapper) {
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
        this.objectMapper = objectMapper;
    }

    public ReviewExecutionContext process(Long taskId) {
        long workerStartNs = System.nanoTime();
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        log.info("review task loaded. taskId={}, taskType={}, status={}, elapsedMs={}",
            task.getId(), task.getTaskType(), task.getStatus(), elapsedMs(workerStartNs));
        if (!canStart(task.getStatus())) {
            throw new DomainException("TASK_STATE_INVALID", "Review task cannot start from status: " + task.getStatus());
        }

        reviewStateService.markTaskRunning(task);
        log.info("review task marked running. taskId={}, elapsedMs={}", task.getId(), elapsedMs(workerStartNs));

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
            long promptStartNs = System.nanoTime();
            ReviewPromptPayload reviewPrompt = promptBuilderService.build(context);
            log.info("review prompt built. taskId={}, files={}, promptBuildMs={}, elapsedMs={}",
                task.getId(),
                reviewPrompt.getFiles() == null ? 0 : reviewPrompt.getFiles().size(),
                elapsedMs(promptStartNs),
                elapsedMs(workerStartNs));
            long llmStartNs = System.nanoTime();
            log.info("review llm call starting. taskId={}, projectId={}, elapsedMs={}",
                task.getId(), task.getProjectId(), elapsedMs(workerStartNs));
            ChatCompletionResponse llmResponse = llmGatewayService.review(task.getProjectId(), reviewPrompt);
            log.info("review llm call finished. taskId={}, llmMs={}, elapsedMs={}",
                task.getId(), elapsedMs(llmStartNs), elapsedMs(workerStartNs));
            ReviewSummary reviewSummary = reviewResultParser.parse(llmResponse);
            reviewScoreService.applyScores(reviewSummary);
            CodeReviewResultEntity persistedResult = reviewResultPersistenceService.persist(
                task.getId(),
                "openai-compatible",
                llmResponse.getModel(),
                reviewSummary,
                llmResponse
            );
            log.info("review result persisted. taskId={}, resultId={}, elapsedMs={}",
                task.getId(), persistedResult.getId(), elapsedMs(workerStartNs));
            if (shouldPublishGitLab(projectConfig)) {
                long publishStartNs = System.nanoTime();
                publishReviewResult(task, persistedResult, gitLabProjectId, gitLabProjectUrl, gitLabApiToken);
                log.info("review gitlab note published. taskId={}, publishMs={}, elapsedMs={}",
                    task.getId(), elapsedMs(publishStartNs), elapsedMs(workerStartNs));
            }

            QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
            commentWrapper.eq("result_id", persistedResult.getId());
            List<CodeReviewCommentEntity> comments = codeReviewCommentMapper.selectList(commentWrapper);
            if (shouldNotifyWeCom(projectConfig)) {
                weComNotificationService.notifyReviewResult(
                    task.getProjectId(),
                    buildNotificationMetadata(task),
                    persistedResult,
                    comments,
                    projectConfig == null ? null : projectConfig.getWecomWebhookUrl()
                );
            }
        } catch (RuntimeException ex) {
            log.warn("review task failed. taskId={}, elapsedMs={}, message={}",
                task.getId(), elapsedMs(workerStartNs), ex.getMessage());
            reviewRetryService.handleFailure(task, ex);
            throw ex;
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
            context.setTargetType("commit");
            changes = gitLabReviewTargetService.getCommitChanges(
                gitLabProjectUrl,
                gitLabProjectId,
                task.getTargetId(),
                task.getTargetTitle(),
                gitLabApiToken
            );
        } else {
            throw new DomainException("TASK_TYPE_UNSUPPORTED", "Unsupported review task type: " + task.getTaskType());
        }
        context.setMergeRequestChanges(changes);
        return context;
    }

    private ReviewNotificationMetadata buildNotificationMetadata(CodeReviewTaskEntity task) {
        ReviewNotificationMetadata metadata = new ReviewNotificationMetadata();
        metadata.setReviewTargetType(task.getTaskType());
        metadata.setTargetId(task.getTargetId());
        metadata.setSubmitMessage(task.getTargetTitle());

        if (task.getEventId() == null) {
            return metadata;
        }
        CodeReviewEventEntity event = reviewEventStoreMapper.selectById(task.getEventId());
        if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
            return metadata;
        }
        try {
            GitLabWebhookPayload payload = objectMapper.readValue(event.getPayloadJson(), GitLabWebhookPayload.class);
            metadata.setSubmitter(resolveSubmitter(payload, event));
            metadata.setSubmitBranch(resolveBranch(payload));
            metadata.setSubmitTime(resolveSubmitTime(payload, task));
            String submitMessage = resolveSubmitMessage(payload, task);
            if (StringUtils.hasText(submitMessage)) {
                metadata.setSubmitMessage(submitMessage);
            }
        } catch (Exception ignored) {
            metadata.setSubmitter(event.getOperatorName());
        }
        return metadata;
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

    private String resolveSubmitTime(GitLabWebhookPayload payload, CodeReviewTaskEntity task) {
        String raw = null;
        if (payload != null && payload.getCommits() != null && !payload.getCommits().isEmpty()) {
            GitLabWebhookPayload.Commit latest = payload.getCommits().get(payload.getCommits().size() - 1);
            raw = latest == null ? null : latest.getTimestamp();
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

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
