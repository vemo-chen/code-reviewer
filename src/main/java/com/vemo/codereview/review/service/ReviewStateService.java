package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewStateService {

    private final ReviewEventStoreMapper codeReviewEventMapper;
    private final ReviewTaskStoreMapper codeReviewTaskMapper;

    public ReviewStateService(ReviewEventStoreMapper codeReviewEventMapper, ReviewTaskStoreMapper codeReviewTaskMapper) {
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewTaskMapper = codeReviewTaskMapper;
    }

    public void markEventTaskCreated(CodeReviewEventEntity event) {
        transitionEvent(event, ReviewEventLifecycle.TASK_CREATED,
            Arrays.asList(ReviewEventLifecycle.RECEIVED, ReviewEventLifecycle.TASK_CREATED));
    }

    public void markEventProcessed(Long eventId) {
        CodeReviewEventEntity event = codeReviewEventMapper.selectById(eventId);
        if (event == null) {
            return;
        }
        event.setStatus(ReviewEventLifecycle.PROCESSED.name());
        event.setUpdatedAt(new Date());
        codeReviewEventMapper.updateById(event);
    }

    public void markEventFailed(Long eventId) {
        CodeReviewEventEntity event = codeReviewEventMapper.selectById(eventId);
        if (event == null) {
            return;
        }
        transitionEvent(event, ReviewEventLifecycle.FAILED,
            Arrays.asList(ReviewEventLifecycle.RECEIVED, ReviewEventLifecycle.TASK_CREATED, ReviewEventLifecycle.FAILED));
    }

    public void markEventIgnored(CodeReviewEventEntity event) {
        transitionEvent(event, ReviewEventLifecycle.IGNORED,
            Arrays.asList(ReviewEventLifecycle.RECEIVED, ReviewEventLifecycle.IGNORED));
    }

    public void markTaskRunning(CodeReviewTaskEntity task) {
        transitionTask(task, ReviewTaskLifecycle.RUNNING,
            Arrays.asList(ReviewTaskLifecycle.PENDING, ReviewTaskLifecycle.FAILED));
        task.setNextRetryAt(null);
        task.setUpdatedAt(new Date());
        if (task.getStartedAt() == null) {
            task.setStartedAt(new Date());
        }
        codeReviewTaskMapper.updateById(task);
    }

    public boolean claimTaskRunning(Long taskId) {
        if (taskId == null) return false;
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", taskId)
            .in("status", Arrays.asList(ReviewTaskLifecycle.PENDING.name(), ReviewTaskLifecycle.FAILED.name()))
            .set("status", ReviewTaskLifecycle.RUNNING.name())
            .set("next_retry_at", null)
            .set("started_at", now)
            .set("updated_at", now);
        return codeReviewTaskMapper.update(null, wrapper) == 1;
    }

    public void markTaskSuccess(CodeReviewTaskEntity task) {
        transitionTask(task, ReviewTaskLifecycle.SUCCESS, Arrays.asList(ReviewTaskLifecycle.RUNNING));
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setFinishedAt(new Date());
        task.setNextRetryAt(null);
        task.setUpdatedAt(new Date());
        codeReviewTaskMapper.updateById(task);
    }

    public void markTaskFailed(CodeReviewTaskEntity task, int retryCount, Date nextRetryAt,
                               String errorCode, String errorMessage) {
        transitionTask(task, ReviewTaskLifecycle.FAILED, Arrays.asList(ReviewTaskLifecycle.RUNNING));
        task.setRetryCount(retryCount);
        task.setNextRetryAt(nextRetryAt);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(new Date());
        task.setUpdatedAt(new Date());
        codeReviewTaskMapper.updateById(task);
    }

    private void transitionEvent(CodeReviewEventEntity event, ReviewEventLifecycle target,
                                 List<ReviewEventLifecycle> allowedSources) {
        ReviewEventLifecycle current = resolveEventStatus(event.getStatus());
        if (!allowedSources.contains(current)) {
            throw new DomainException("EVENT_STATE_INVALID",
                "Invalid event status transition: " + current + " -> " + target);
        }
        event.setStatus(target.name());
        event.setUpdatedAt(new Date());
        codeReviewEventMapper.updateById(event);
    }

    private void transitionTask(CodeReviewTaskEntity task, ReviewTaskLifecycle target,
                                List<ReviewTaskLifecycle> allowedSources) {
        ReviewTaskLifecycle current = resolveTaskStatus(task.getStatus());
        if (!allowedSources.contains(current)) {
            throw new DomainException("TASK_STATE_INVALID",
                "Invalid review task status transition: " + current + " -> " + target);
        }
        task.setStatus(target.name());
    }

    private ReviewEventLifecycle resolveEventStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new DomainException("EVENT_STATE_INVALID", "Review event status is empty");
        }
        if ("PENDING".equalsIgnoreCase(status)) {
            return ReviewEventLifecycle.TASK_CREATED;
        }
        return ReviewEventLifecycle.valueOf(status);
    }

    private ReviewTaskLifecycle resolveTaskStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new DomainException("TASK_STATE_INVALID", "Review task status is empty");
        }
        return ReviewTaskLifecycle.valueOf(status);
    }
}
