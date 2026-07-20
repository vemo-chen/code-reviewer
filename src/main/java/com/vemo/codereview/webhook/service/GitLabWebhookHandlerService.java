package com.vemo.codereview.webhook.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.common.service.IdempotencyService;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.platform.gitlab.service.GitLabCommentPublisher;
import com.vemo.codereview.project.service.ProjectConfigService;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.review.service.ReviewStateService;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.model.MergePushCorrelationResult;
import com.vemo.codereview.webhook.model.MergePushDecision;
import com.vemo.codereview.webhook.model.StandardReviewEvent;
import com.vemo.codereview.webhook.support.GitLabWebhookEventNormalizer;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class GitLabWebhookHandlerService {

    private final GitLabWebhookEventNormalizer gitLabWebhookEventNormalizer;
    private final IdempotencyService idempotencyService;
    private final ReviewEventStoreMapper codeReviewEventMapper;
    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewStateService reviewStateService;
    private final ProjectConfigService projectConfigService;
    private final MergeRequestEventService mergeRequestEventService;
    private final MergePushCorrelationService mergePushCorrelationService;
    private final GitLabCommentPublisher gitLabCommentPublisher;

    public GitLabWebhookHandlerService(
        GitLabWebhookEventNormalizer gitLabWebhookEventNormalizer,
        IdempotencyService idempotencyService,
        ReviewEventStoreMapper codeReviewEventMapper,
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewResultStoreMapper codeReviewResultMapper,
        ReviewStateService reviewStateService,
        ProjectConfigService projectConfigService,
        MergeRequestEventService mergeRequestEventService,
        MergePushCorrelationService mergePushCorrelationService,
        GitLabCommentPublisher gitLabCommentPublisher) {
        this.gitLabWebhookEventNormalizer = gitLabWebhookEventNormalizer;
        this.idempotencyService = idempotencyService;
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.reviewStateService = reviewStateService;
        this.projectConfigService = projectConfigService;
        this.mergeRequestEventService = mergeRequestEventService;
        this.mergePushCorrelationService = mergePushCorrelationService;
        this.gitLabCommentPublisher = gitLabCommentPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleWebhook(String token, GitLabWebhookPayload payload) {
        long startNs = System.nanoTime();
        validateToken(token, payload);
        if (payload == null || payload.getObjectKind() == null) {
            throw new DomainException("UNSUPPORTED_EVENT", "GitLab webhook payload is invalid");
        }
        log.info("webhook validated. eventType={}, projectId={}, elapsedMs={}",
            payload.getObjectKind(),
            payload.getProject() == null ? null : payload.getProject().getId(),
            elapsedMs(startNs));

        if ("merge_request".equals(payload.getObjectKind())) {
            StandardReviewEvent event = gitLabWebhookEventNormalizer.normalizeMergeRequest(payload);
            log.info("webhook normalized. eventType={}, idempotentKey={}, elapsedMs={}",
                payload.getObjectKind(), event.getIdempotentKey(), elapsedMs(startNs));
            ProjectProfileEntity project = resolveManagedProject(event, startNs);
            if (project == null || !Boolean.TRUE.equals(project.getActive())
                || !Boolean.TRUE.equals(project.getAiReviewEnabled())
                || !isBranchReviewAllowed(project, event.getSubmitBranch())) {
                createIgnoredEvent(event);
                return;
            }
            mergeRequestEventService.handle(event, project);
            return;
        }
        if ("push".equals(payload.getObjectKind())) {
            StandardReviewEvent event = gitLabWebhookEventNormalizer.normalizePush(payload);
            log.info("webhook normalized. eventType={}, idempotentKey={}, elapsedMs={}",
                payload.getObjectKind(), event.getIdempotentKey(), elapsedMs(startNs));
            ProjectProfileEntity project = resolveManagedProject(event, startNs);
            if (isDeletedBranch(payload) || isEmptyNewBranch(payload)) {
                createIgnoredEvent(event);
                log.info("webhook push ignored. idempotentKey={}, elapsedMs={}",
                    event.getIdempotentKey(), elapsedMs(startNs));
                return;
            }
            if (project != null) {
                MergePushCorrelationResult correlation = mergePushCorrelationService.correlate(payload, project);
                MergePushDecision decision = correlation.getDecision();
                if (decision == MergePushDecision.SKIP_ALREADY_REVIEWED) {
                    boolean firstDelivery = createIgnoredEvent(event);
                    if (firstDelivery && Boolean.TRUE.equals(project.getGitlabNoteEnabled())) {
                        publishMergedMrCommit(project, event, payload, correlation.getReviewedMrTaskId());
                    }
                    log.info("webhook push ignored by merge correlation. decision={}, idempotentKey={}, elapsedMs={}",
                        decision, event.getIdempotentKey(), elapsedMs(startNs));
                    return;
                }
                if (decision == MergePushDecision.IGNORE_NO_CODE) {
                    createIgnoredEvent(event);
                    log.info("webhook push ignored by merge correlation. decision={}, idempotentKey={}, elapsedMs={}",
                        decision, event.getIdempotentKey(), elapsedMs(startNs));
                    return;
                }
            }
            handleEvent(event, "PUSH_REVIEW", startNs, project);
            return;
        }
        throw new DomainException("UNSUPPORTED_EVENT", "Only merge_request and push events are supported");
    }

    private void handleEvent(StandardReviewEvent event, String taskType, long webhookStartNs) {
        handleEvent(event, taskType, webhookStartNs, null);
    }

    private void handleEvent(StandardReviewEvent event, String taskType, long webhookStartNs,
                             ProjectProfileEntity resolvedProject) {
        if (isDuplicateEvent(event.getIdempotentKey())) {
            log.info("webhook ignored as duplicate. idempotentKey={}, elapsedMs={}",
                event.getIdempotentKey(), elapsedMs(webhookStartNs));
            return;
        }
        ProjectProfileEntity project = resolvedProject == null ? resolveManagedProject(event, webhookStartNs)
            : resolvedProject;
        if (project == null) {
            createIgnoredEvent(event);
            log.info("webhook ignored because project not found. idempotentKey={}, elapsedMs={}",
                event.getIdempotentKey(), elapsedMs(webhookStartNs));
            return;
        }
        bindProjectContext(event, project);
        if (!Boolean.TRUE.equals(project.getActive()) || !Boolean.TRUE.equals(project.getAiReviewEnabled())) {
            createIgnoredEvent(event);
            log.info("webhook ignored because project disabled. projectId={}, active={}, aiReviewEnabled={}, elapsedMs={}",
                project.getId(), project.getActive(), project.getAiReviewEnabled(), elapsedMs(webhookStartNs));
            return;
        }
        if (!isBranchReviewAllowed(project, event.getSubmitBranch())) {
            createIgnoredEvent(event);
            log.info("webhook ignored because branch is not configured for review. projectId={}, branch={}, reviewBranches={}, elapsedMs={}",
                project.getId(), event.getSubmitBranch(), project.getReviewBranches(), elapsedMs(webhookStartNs));
            return;
        }
        createReviewTask(event, taskType, webhookStartNs);
    }

    private ProjectProfileEntity resolveManagedProject(StandardReviewEvent event, long webhookStartNs) {
        if (event == null) {
            return null;
        }
        Long gitLabProjectId = event.getProjectId();
        ProjectProfileEntity project = projectConfigService.findByGitLabProjectId(gitLabProjectId);
        log.info("webhook project lookup finished. gitlabProjectId={}, matchedProjectId={}, elapsedMs={}",
            gitLabProjectId, project == null ? null : project.getId(), elapsedMs(webhookStartNs));
        if (project != null) {
            bindProjectContext(event, project);
        }
        return project;
    }

    private void bindProjectContext(StandardReviewEvent event, ProjectProfileEntity project) {
        if (event == null || project == null) {
            return;
        }
        event.setProjectId(project.getId());
        if (StringUtils.hasText(project.getProjectName())) {
            event.setProjectName(project.getProjectName());
        }
    }

    private boolean createIgnoredEvent(StandardReviewEvent event) {
        CodeReviewEventEntity eventEntity = buildEventEntity(event, new Date());
        try {
            codeReviewEventMapper.insert(eventEntity);
        } catch (DuplicateKeyException ex) {
            return false;
        }
        reviewStateService.markEventIgnored(eventEntity);
        return true;
    }

    private void publishMergedMrCommit(ProjectProfileEntity project, StandardReviewEvent event,
                                       GitLabWebhookPayload payload, Long reviewedMrTaskId) {
        if (reviewedMrTaskId == null) {
            log.warn("merge commit note skipped because reviewed MR task id is missing. mergeSha={}",
                payload == null ? null : payload.getAfter());
            return;
        }
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(reviewedMrTaskId);
        if (task == null) {
            log.warn("merge commit note skipped because MR task is missing. taskId={}, mergeSha={}",
                reviewedMrTaskId, payload == null ? null : payload.getAfter());
            return;
        }
        CodeReviewResultEntity result = codeReviewResultMapper.selectOne(new QueryWrapper<CodeReviewResultEntity>()
            .eq("task_id", reviewedMrTaskId)
            .orderByDesc("id")
            .last("limit 1"));
        if (result == null) {
            log.warn("merge commit note skipped because MR result is missing. taskId={}, mergeSha={}",
                reviewedMrTaskId, payload == null ? null : payload.getAfter());
            return;
        }
        gitLabCommentPublisher.publishMergedMrCommit(
            project.getGitlabProjectUrl(),
            project.getGitlabProjectId(),
            payload == null ? null : payload.getAfter(),
            task.getTargetTitle(),
            event == null ? null : event.getSubmitBranch(),
            result,
            project.getGitlabWebhookToken());
    }

    private void createReviewTask(StandardReviewEvent event, String taskType, long webhookStartNs) {
        Date now = new Date();
        CodeReviewEventEntity eventEntity = buildEventEntity(event, now);
        try {
            codeReviewEventMapper.insert(eventEntity);
        } catch (DuplicateKeyException ex) {
            log.info("webhook event insert duplicated. idempotentKey={}, elapsedMs={}",
                event.getIdempotentKey(), elapsedMs(webhookStartNs));
            return;
        }
        log.info("review event inserted. eventId={}, taskType={}, elapsedMs={}",
            eventEntity.getId(), taskType, elapsedMs(webhookStartNs));

        CodeReviewTaskEntity taskEntity = new CodeReviewTaskEntity();
        taskEntity.setEventId(eventEntity.getId());
        taskEntity.setTaskType(taskType);
        taskEntity.setSourcePlatform(event.getSourcePlatform());
        taskEntity.setProjectId(event.getProjectId());
        taskEntity.setTargetId(event.getTargetId());
        taskEntity.setTargetTitle(event.getTargetTitle());
        taskEntity.setStatus(ReviewTaskLifecycle.PENDING.name());
        taskEntity.setRetryCount(0);
        taskEntity.setCreatedAt(now);
        taskEntity.setUpdatedAt(now);
        codeReviewTaskMapper.insert(taskEntity);
        reviewStateService.markEventTaskCreated(eventEntity);
        log.info("review task created. eventId={}, taskId={}, taskType={}, elapsedMs={}",
            eventEntity.getId(), taskEntity.getId(), taskType, elapsedMs(webhookStartNs));
        log.info("review task persisted for database polling. taskId={}, elapsedMs={}",
            taskEntity.getId(), elapsedMs(webhookStartNs));
    }

    private CodeReviewEventEntity buildEventEntity(StandardReviewEvent event, Date now) {
        CodeReviewEventEntity eventEntity = new CodeReviewEventEntity();
        eventEntity.setSourcePlatform(event.getSourcePlatform());
        eventEntity.setEventType(event.getEventType());
        eventEntity.setProjectId(event.getProjectId());
        eventEntity.setProjectName(event.getProjectName());
        eventEntity.setObjectId(event.getObjectId());
        eventEntity.setObjectType(event.getObjectType());
        eventEntity.setOperatorId(event.getOperatorId());
        eventEntity.setOperatorName(event.getOperatorName());
        eventEntity.setSubmitBranch(event.getSubmitBranch());
        eventEntity.setSubmitTime(event.getSubmitTime());
        eventEntity.setMrState(event.getMrState());
        eventEntity.setMrHeadSha(event.getMrHeadSha());
        eventEntity.setMergedSha(event.getMergedSha());
        eventEntity.setIdempotentKey(event.getIdempotentKey());
        eventEntity.setPayloadJson(event.getPayloadJson());
        eventEntity.setStatus(ReviewEventLifecycle.RECEIVED.name());
        eventEntity.setCreatedAt(now);
        eventEntity.setUpdatedAt(now);
        return eventEntity;
    }

    private boolean isDuplicateEvent(String idempotentKey) {
        QueryWrapper<CodeReviewEventEntity> wrapper = new QueryWrapper<CodeReviewEventEntity>();
        wrapper.eq("idempotent_key", idempotentKey);
        Long count = codeReviewEventMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            return true;
        }
        return !idempotencyService.tryAcquire(idempotentKey);
    }

    private void validateToken(String token, GitLabWebhookPayload payload) {
        String expectedToken = resolveExpectedToken(payload);
        if (!StringUtils.hasText(expectedToken) || token == null || !token.equals(expectedToken)) {
            throw new DomainException("INVALID_TOKEN", "Invalid GitLab webhook token");
        }
    }

    private String resolveExpectedToken(GitLabWebhookPayload payload) {
        Long gitLabProjectId = payload == null || payload.getProject() == null ? null : payload.getProject().getId();
        if (gitLabProjectId == null) {
            return null;
        }
        ProjectProfileEntity project = projectConfigService.findByGitLabProjectId(gitLabProjectId);
        if (project == null) {
            return null;
        }
        if (StringUtils.hasText(project.getGitlabWebhookToken())) {
            return project.getGitlabWebhookToken().trim();
        }
        return null;
    }

    private boolean isDeletedBranch(GitLabWebhookPayload payload) {
        return isZeroSha(payload.getAfter());
    }

    private boolean isEmptyNewBranch(GitLabWebhookPayload payload) {
        return isZeroSha(payload.getBefore()) && (payload.getCommits() == null || payload.getCommits().isEmpty());
    }

    private boolean isZeroSha(String sha) {
        return StringUtils.hasText(sha) && sha.trim().matches("0+");
    }

    private boolean isBranchReviewAllowed(ProjectProfileEntity project, String submitBranch) {
        Set<String> reviewBranches = parseReviewBranches(project == null ? null : project.getReviewBranches());
        if (reviewBranches.isEmpty()) {
            return true;
        }
        return StringUtils.hasText(submitBranch) && reviewBranches.contains(submitBranch.trim());
    }

    private Set<String> parseReviewBranches(String reviewBranches) {
        Set<String> result = new LinkedHashSet<String>();
        if (!StringUtils.hasText(reviewBranches)) {
            return result;
        }
        String[] branches = reviewBranches.split("[,;\\s]+");
        for (String branch : branches) {
            if (StringUtils.hasText(branch)) {
                result.add(branch.trim());
            }
        }
        return result;
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
