package com.vemo.codereview.webhook.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.common.service.IdempotencyService;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.project.service.ProjectConfigService;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.review.service.ReviewStateService;
import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.model.StandardReviewEvent;
import com.vemo.codereview.webhook.support.GitLabWebhookEventNormalizer;
import java.util.ArrayList;
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
    private final ReviewTaskDispatcher reviewTaskDispatcher;
    private final ReviewStateService reviewStateService;
    private final ProjectConfigService projectConfigService;

    public GitLabWebhookHandlerService(
        GitLabWebhookEventNormalizer gitLabWebhookEventNormalizer,
        IdempotencyService idempotencyService,
        ReviewEventStoreMapper codeReviewEventMapper,
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewTaskDispatcher reviewTaskDispatcher,
        ReviewStateService reviewStateService,
        ProjectConfigService projectConfigService) {
        this.gitLabWebhookEventNormalizer = gitLabWebhookEventNormalizer;
        this.idempotencyService = idempotencyService;
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.reviewTaskDispatcher = reviewTaskDispatcher;
        this.reviewStateService = reviewStateService;
        this.projectConfigService = projectConfigService;
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
            handleEvent(event, "MR_REVIEW", startNs);
            return;
        }
        if ("push".equals(payload.getObjectKind())) {
            List<StandardReviewEvent> events = gitLabWebhookEventNormalizer.normalizePushCommits(payload);
            log.info("webhook normalized. eventType={}, commitEvents={}, elapsedMs={}",
                payload.getObjectKind(), events.size(), elapsedMs(startNs));
            List<StandardReviewEvent> reviewableEvents = filterReviewablePushEvents(events, startNs);
            if (reviewableEvents.isEmpty()) {
                StandardReviewEvent ignoredEvent = events.isEmpty() ? gitLabWebhookEventNormalizer.normalizePush(payload)
                    : events.get(events.size() - 1);
                resolveManagedProject(ignoredEvent, startNs);
                createIgnoredEvent(ignoredEvent);
                log.info("webhook ignored because push contains only merge commits. idempotentKey={}, commitEvents={}, elapsedMs={}",
                    ignoredEvent.getIdempotentKey(), events.size(), elapsedMs(startNs));
                return;
            }
            for (StandardReviewEvent event : reviewableEvents) {
                handleEvent(event, "PUSH_REVIEW", startNs);
            }
            return;
        }
        throw new DomainException("UNSUPPORTED_EVENT", "Only merge_request and push events are supported");
    }

    private List<StandardReviewEvent> filterReviewablePushEvents(List<StandardReviewEvent> events, long webhookStartNs) {
        List<StandardReviewEvent> reviewableEvents = new ArrayList<StandardReviewEvent>();
        if (events == null) {
            return reviewableEvents;
        }
        for (StandardReviewEvent event : events) {
            if (shouldIgnorePushMergeCommit(event)) {
                log.info("push commit skipped as merge commit. commitSha={}, title={}, elapsedMs={}",
                    event.getTargetId(), event.getTargetTitle(), elapsedMs(webhookStartNs));
                continue;
            }
            reviewableEvents.add(event);
        }
        return reviewableEvents;
    }

    private void handleEvent(StandardReviewEvent event, String taskType, long webhookStartNs) {
        if (isDuplicateEvent(event.getIdempotentKey())) {
            log.info("webhook ignored as duplicate. idempotentKey={}, elapsedMs={}",
                event.getIdempotentKey(), elapsedMs(webhookStartNs));
            return;
        }
        ProjectProfileEntity project = resolveManagedProject(event, webhookStartNs);
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

    private void createIgnoredEvent(StandardReviewEvent event) {
        CodeReviewEventEntity eventEntity = buildEventEntity(event, new Date());
        try {
            codeReviewEventMapper.insert(eventEntity);
        } catch (DuplicateKeyException ex) {
            return;
        }
        reviewStateService.markEventIgnored(eventEntity);
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
        reviewTaskDispatcher.dispatch(taskEntity.getId());
        log.info("review task dispatched. taskId={}, elapsedMs={}", taskEntity.getId(), elapsedMs(webhookStartNs));
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

    private boolean shouldIgnorePushMergeCommit(StandardReviewEvent event) {
        if (event == null || !"commit".equalsIgnoreCase(event.getObjectType())) {
            return false;
        }
        String title = event.getTargetTitle();
        if (title == null) {
            return false;
        }
        return title.trim().startsWith("Merge ");
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
