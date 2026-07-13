package com.vemo.codereview.webhook.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.review.service.ReviewResultPersistenceService;
import com.vemo.codereview.review.service.ReviewStateService;
import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import com.vemo.codereview.webhook.model.StandardReviewEvent;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class MergeRequestEventService {

    private final ReviewEventStoreMapper eventMapper;
    private final ReviewTaskStoreMapper taskMapper;
    private final ReviewResultPersistenceService resultPersistenceService;
    private final ReviewStateService reviewStateService;
    private final ReviewTaskDispatcher taskDispatcher;

    public MergeRequestEventService(ReviewEventStoreMapper eventMapper, ReviewTaskStoreMapper taskMapper,
                                    ReviewResultPersistenceService resultPersistenceService,
                                    ReviewStateService reviewStateService, ReviewTaskDispatcher taskDispatcher) {
        this.eventMapper = eventMapper;
        this.taskMapper = taskMapper;
        this.resultPersistenceService = resultPersistenceService;
        this.reviewStateService = reviewStateService;
        this.taskDispatcher = taskDispatcher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(StandardReviewEvent incoming, ProjectProfileEntity project) {
        CodeReviewEventEntity existing = eventMapper.selectOne(new QueryWrapper<CodeReviewEventEntity>()
            .eq("idempotent_key", incoming.getIdempotentKey()).last("FOR UPDATE"));
        if (existing == null) {
            create(incoming, project);
            return;
        }
        boolean headChanged = StringUtils.hasText(incoming.getMrHeadSha())
            && !incoming.getMrHeadSha().equals(existing.getMrHeadSha());
        updateEvent(existing, incoming);
        eventMapper.updateById(existing);
        if (!headChanged) {
            return;
        }
        CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>()
            .eq("event_id", existing.getId()).last("FOR UPDATE"));
        if (task == null) {
            createTask(existing, incoming, project);
            return;
        }
        resultPersistenceService.deleteByTaskId(task.getId());
        resetTask(task, incoming);
        taskMapper.updateById(task);
        taskDispatcher.dispatch(task.getId());
    }

    private void create(StandardReviewEvent incoming, ProjectProfileEntity project) {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform(incoming.getSourcePlatform());
        event.setEventType(incoming.getEventType());
        event.setProjectId(project.getId());
        event.setProjectName(project.getProjectName());
        event.setObjectId(incoming.getObjectId());
        event.setObjectType(incoming.getObjectType());
        event.setOperatorId(incoming.getOperatorId());
        event.setOperatorName(incoming.getOperatorName());
        event.setSubmitBranch(incoming.getSubmitBranch());
        event.setSubmitTime(incoming.getSubmitTime());
        event.setMrState(incoming.getMrState());
        event.setMrHeadSha(incoming.getMrHeadSha());
        event.setMergedSha(incoming.getMergedSha());
        event.setIdempotentKey(incoming.getIdempotentKey());
        event.setPayloadJson(incoming.getPayloadJson());
        event.setStatus(ReviewEventLifecycle.RECEIVED.name());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);
        if (!StringUtils.hasText(event.getOperatorId())) {
            log.warn("MR_CREATOR_MISSING idempotentKey={}", event.getIdempotentKey());
        }
        createTask(event, incoming, project);
    }

    private void createTask(CodeReviewEventEntity event, StandardReviewEvent incoming, ProjectProfileEntity project) {
        Date now = new Date();
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform(incoming.getSourcePlatform());
        task.setProjectId(project.getId());
        task.setTargetId(incoming.getTargetId());
        task.setTargetTitle(incoming.getTargetTitle());
        task.setStatus(ReviewTaskLifecycle.PENDING.name());
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        reviewStateService.markEventTaskCreated(event);
        taskDispatcher.dispatch(task.getId());
    }

    private void updateEvent(CodeReviewEventEntity existing, StandardReviewEvent incoming) {
        existing.setEventType(incoming.getEventType());
        existing.setProjectName(incoming.getProjectName());
        existing.setSubmitBranch(incoming.getSubmitBranch());
        existing.setMrState(incoming.getMrState());
        existing.setMrHeadSha(incoming.getMrHeadSha());
        existing.setMergedSha(incoming.getMergedSha());
        existing.setPayloadJson(incoming.getPayloadJson());
        existing.setUpdatedAt(new Date());
        if (!StringUtils.hasText(existing.getOperatorId()) && StringUtils.hasText(incoming.getOperatorId())) {
            existing.setOperatorId(incoming.getOperatorId());
            existing.setOperatorName(incoming.getOperatorName());
        }
    }

    private void resetTask(CodeReviewTaskEntity task, StandardReviewEvent incoming) {
        task.setTargetId(incoming.getTargetId());
        task.setTargetTitle(incoming.getTargetTitle());
        task.setStatus(ReviewTaskLifecycle.PENDING.name());
        task.setFixStatus(null);
        task.setFixSubmittedBy(null);
        task.setFixSubmittedAt(null);
        task.setFixReviewedBy(null);
        task.setFixReviewedAt(null);
        task.setFixReviewComment(null);
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(new Date());
    }
}
