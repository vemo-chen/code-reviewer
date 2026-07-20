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
import org.springframework.util.StringUtils;

@Service
public class ReviewStateService {

    private static final int MAX_RETRY_COUNT = 3;

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
        return claimTaskRunning(taskId, java.util.UUID.randomUUID().toString());
    }

    public boolean claimTaskRunning(Long taskId, String executionToken) {
        if (taskId == null) return false;
        if (!StringUtils.hasText(executionToken)) return false;
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", taskId)
            .and(statusWrapper -> statusWrapper
                .eq("status", ReviewTaskLifecycle.PENDING.name())
                .or(failedWrapper -> failedWrapper
                    .eq("status", ReviewTaskLifecycle.FAILED.name())
                    .le("retry_count", MAX_RETRY_COUNT)
                    .isNotNull("next_retry_at")
                    .le("next_retry_at", now)))
            .set("status", ReviewTaskLifecycle.RUNNING.name())
            .set("next_retry_at", null)
            .set("execution_token", executionToken)
            .set("started_at", now)
            .set("updated_at", now);
        return codeReviewTaskMapper.update(null, wrapper) == 1;
    }

    public void markTaskSuccess(CodeReviewTaskEntity task) {
        transitionTask(task, ReviewTaskLifecycle.SUCCESS, Arrays.asList(ReviewTaskLifecycle.RUNNING));
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", task.getId())
            .set("status", ReviewTaskLifecycle.SUCCESS.name())
            .set("error_code", null)
            .set("error_message", null)
            .set("finished_at", now)
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("updated_at", now);
        codeReviewTaskMapper.update(null, wrapper);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setFinishedAt(now);
        task.setNextRetryAt(null);
        task.setExecutionToken(null);
        task.setUpdatedAt(now);
    }

    public void markTaskFailed(CodeReviewTaskEntity task, int retryCount, Date nextRetryAt,
                               String errorCode, String errorMessage) {
        transitionTask(task, ReviewTaskLifecycle.FAILED, Arrays.asList(ReviewTaskLifecycle.RUNNING));
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", task.getId())
            .set("status", ReviewTaskLifecycle.FAILED.name())
            .set("retry_count", retryCount)
            .set("next_retry_at", nextRetryAt)
            .set("execution_token", null)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", now)
            .set("updated_at", now);
        codeReviewTaskMapper.update(null, wrapper);
        task.setRetryCount(retryCount);
        task.setNextRetryAt(nextRetryAt);
        task.setExecutionToken(null);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
    }

    public boolean markTaskFailedIfCurrent(CodeReviewTaskEntity task, String expectedExecutionToken, int retryCount,
                                           Date nextRetryAt, String errorCode, String errorMessage) {
        if (task == null || task.getId() == null || !StringUtils.hasText(expectedExecutionToken)) {
            return false;
        }
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", task.getId())
            .eq("status", ReviewTaskLifecycle.RUNNING.name())
            .eq("execution_token", expectedExecutionToken)
            .set("status", ReviewTaskLifecycle.FAILED.name())
            .set("retry_count", retryCount)
            .set("next_retry_at", nextRetryAt)
            .set("execution_token", null)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", now)
            .set("updated_at", now);
        boolean updated = codeReviewTaskMapper.update(null, wrapper) == 1;
        if (updated) {
            task.setStatus(ReviewTaskLifecycle.FAILED.name());
            task.setRetryCount(retryCount);
            task.setNextRetryAt(nextRetryAt);
            task.setExecutionToken(null);
            task.setErrorCode(errorCode);
            task.setErrorMessage(errorMessage);
            task.setFinishedAt(now);
            task.setUpdatedAt(now);
        }
        return updated;
    }

    public boolean markRunningTaskFailedIfCurrent(Long taskId, String expectedExecutionToken,
                                                  String errorCode, String errorMessage) {
        if (taskId == null || !StringUtils.hasText(expectedExecutionToken)) {
            return false;
        }
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", taskId)
            .eq("status", ReviewTaskLifecycle.RUNNING.name())
            .eq("execution_token", expectedExecutionToken)
            .set("status", ReviewTaskLifecycle.FAILED.name())
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", now)
            .set("updated_at", now);
        return codeReviewTaskMapper.update(null, wrapper) == 1;
    }

    public boolean interruptRunningTask(Long taskId) {
        if (taskId == null) {
            return false;
        }
        Date now = new Date();
        UpdateWrapper<CodeReviewTaskEntity> wrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        wrapper.eq("id", taskId)
            .eq("status", ReviewTaskLifecycle.RUNNING.name())
            .set("status", ReviewTaskLifecycle.FAILED.name())
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("error_code", "USER_INTERRUPTED")
            .set("error_message", "用户停止审查")
            .set("finished_at", now)
            .set("updated_at", now);
        return codeReviewTaskMapper.update(null, wrapper) == 1;
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
