package com.vemo.codereview.review.service;

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
    private final ReviewTaskDispatcher reviewTaskDispatcher;
    private final ProjectPermissionService projectPermissionService;

    public ReviewTaskManualRetryService(
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewTaskDispatcher reviewTaskDispatcher,
        ProjectPermissionService projectPermissionService) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.reviewTaskDispatcher = reviewTaskDispatcher;
        this.projectPermissionService = projectPermissionService;
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

        task.setStatus(ReviewTaskLifecycle.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(new Date());
        codeReviewTaskMapper.updateById(task);

        reviewTaskDispatcher.dispatch(taskId);
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
