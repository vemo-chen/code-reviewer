package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.model.BatchRetryResponse;
import com.vemo.codereview.project.service.ProjectPermissionService;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class ReviewTaskManualRetryService {

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ProjectPermissionService projectPermissionService;
    private final ReviewStateService reviewStateService;

    public ReviewTaskManualRetryService(
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ProjectPermissionService projectPermissionService,
        ReviewStateService reviewStateService) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.projectPermissionService = projectPermissionService;
        this.reviewStateService = reviewStateService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(Long taskId) {
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        projectPermissionService.requireProjectAccess(task.getProjectId());
        if (!canManualRetry(task.getStatus())) {
            throw new DomainException("TASK_RETRY_INVALID", "Only finished review tasks can be retried");
        }

        resetAndDispatch(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void interrupt(Long taskId) {
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        projectPermissionService.requireProjectAccess(task.getProjectId());
        if (!ReviewTaskLifecycle.RUNNING.name().equals(task.getStatus())) {
            throw new DomainException("TASK_INTERRUPT_INVALID", "Only running review tasks can be stopped");
        }
        if (!reviewStateService.interruptRunningTask(taskId)) {
            throw new DomainException("TASK_INTERRUPT_INVALID", "Review task is no longer running");
        }
    }

    public void resetAndDispatch(CodeReviewTaskEntity task) {
        if (task == null || task.getId() == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        Date now = new Date();
        task.setStatus(ReviewTaskLifecycle.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setExecutionToken(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(now);

        UpdateWrapper<CodeReviewTaskEntity> updateWrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        updateWrapper.eq("id", task.getId())
            .set("status", ReviewTaskLifecycle.PENDING.name())
            .set("retry_count", 0)
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("error_code", null)
            .set("error_message", null)
            .set("started_at", null)
            .set("finished_at", null)
            .set("updated_at", now);
        codeReviewTaskMapper.update(null, updateWrapper);

    }
    public BatchRetryResponse batchRetry(List<Long> taskIds) {
        if (CollectionUtils.isEmpty(taskIds)) {
            throw new DomainException("TASK_IDS_REQUIRED", "Task ids are required");
        }

        Set<Long> uniqueTaskIds = new LinkedHashSet<Long>(taskIds);
        List<BatchRetryResponse.Item> results = new ArrayList<BatchRetryResponse.Item>();
        int successCount = 0;
        for (Long taskId : uniqueTaskIds) {
            BatchRetryResponse.Item item = new BatchRetryResponse.Item();
            item.setTaskId(taskId);
            try {
                retry(taskId);
                item.setSuccess(Boolean.TRUE);
                item.setMessage("Review task resubmitted");
                successCount++;
            } catch (RuntimeException ex) {
                item.setSuccess(Boolean.FALSE);
                item.setMessage(ex.getMessage());
            }
            results.add(item);
        }

        BatchRetryResponse response = new BatchRetryResponse();
        response.setTotal(results.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(results.size() - successCount);
        response.setResults(results);
        return response;
    }

    private boolean canManualRetry(String status) {
        return ReviewTaskLifecycle.SUCCESS.name().equals(status)
            || ReviewTaskLifecycle.FAILED.name().equals(status);
    }
}
