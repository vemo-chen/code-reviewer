package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewRetryService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int ORPHAN_PENDING_MINUTES = 10;

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewStateService reviewStateService;

    public ReviewRetryService(ReviewTaskStoreMapper codeReviewTaskMapper, ReviewStateService reviewStateService) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.reviewStateService = reviewStateService;
    }

    public void handleFailure(CodeReviewTaskEntity task, RuntimeException ex) {
        String errorCode = extractErrorCode(ex);
        String errorMessage = trimMessage(ex == null ? null : ex.getMessage());

        Integer currentRetry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetryCount = currentRetry + 1;
        Date nextRetryAt = currentRetry < MAX_RETRY_COUNT ? buildNextRetryAt(nextRetryCount) : null;

        reviewStateService.markTaskFailed(task, nextRetryCount, nextRetryAt, errorCode, errorMessage);
        if (nextRetryCount > MAX_RETRY_COUNT) {
            reviewStateService.markEventFailed(task.getEventId());
        }
    }

    public List<CodeReviewTaskEntity> findRecoverableTasks(Date now) {
        List<CodeReviewTaskEntity> recoverableTasks = new ArrayList<CodeReviewTaskEntity>();
        recoverableTasks.addAll(findRetryableFailedTasks(now));
        recoverableTasks.addAll(findOrphanPendingTasks(now));
        return recoverableTasks;
    }

    private List<CodeReviewTaskEntity> findRetryableFailedTasks(Date now) {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        wrapper.eq("status", "FAILED")
            .lt("retry_count", MAX_RETRY_COUNT)
            .isNotNull("next_retry_at")
            .le("next_retry_at", now);
        return codeReviewTaskMapper.selectList(wrapper);
    }

    private List<CodeReviewTaskEntity> findOrphanPendingTasks(Date now) {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        wrapper.eq("status", "PENDING");
        List<CodeReviewTaskEntity> pendingTasks = codeReviewTaskMapper.selectList(wrapper);
        List<CodeReviewTaskEntity> recoverableTasks = new ArrayList<CodeReviewTaskEntity>();
        for (CodeReviewTaskEntity task : pendingTasks) {
            if (isOrphanPending(task, now)) {
                recoverableTasks.add(task);
            }
        }
        return recoverableTasks;
    }

    private String extractErrorCode(RuntimeException ex) {
        if (ex instanceof DomainException) {
            return ((DomainException) ex).getCode();
        }
        return "REVIEW_EXECUTION_ERROR";
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private Date buildNextRetryAt(int retryCount) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, retryCount);
        return calendar.getTime();
    }

    private boolean isOrphanPending(CodeReviewTaskEntity task, Date now) {
        if (task.getRetryCount() != null && task.getRetryCount() > 0) {
            return false;
        }
        if (task.getStartedAt() != null) {
            return false;
        }
        Date baseTime = task.getCreatedAt() == null ? task.getUpdatedAt() : task.getCreatedAt();
        if (baseTime == null) {
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, -ORPHAN_PENDING_MINUTES);
        return !baseTime.after(calendar.getTime());
    }
}
