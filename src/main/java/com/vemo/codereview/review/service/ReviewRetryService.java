package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewRetryService {

    private static final int MAX_RETRY_COUNT = 3;

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewStateService reviewStateService;

    public ReviewRetryService(ReviewTaskStoreMapper codeReviewTaskMapper, ReviewStateService reviewStateService) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.reviewStateService = reviewStateService;
    }

    public void handleFailure(CodeReviewTaskEntity task, RuntimeException ex) {
        FailureUpdate failureUpdate = buildFailureUpdate(task, ex);
        reviewStateService.markTaskFailed(
            task, failureUpdate.nextRetryCount, failureUpdate.nextRetryAt,
            failureUpdate.errorCode, failureUpdate.errorMessage);
        if (failureUpdate.nextRetryCount > MAX_RETRY_COUNT) {
            reviewStateService.markEventFailed(task.getEventId());
        }
    }

    public boolean handleFailure(CodeReviewTaskEntity task, String expectedExecutionToken, RuntimeException ex) {
        FailureUpdate failureUpdate = buildFailureUpdate(task, ex);

        boolean updated = reviewStateService.markTaskFailedIfCurrent(
            task, expectedExecutionToken, failureUpdate.nextRetryCount, failureUpdate.nextRetryAt,
            failureUpdate.errorCode, failureUpdate.errorMessage);
        if (!updated) {
            return false;
        }
        if (failureUpdate.nextRetryCount > MAX_RETRY_COUNT) {
            reviewStateService.markEventFailed(task.getEventId());
        }
        return true;
    }

    private FailureUpdate buildFailureUpdate(CodeReviewTaskEntity task, RuntimeException ex) {
        String errorCode = extractErrorCode(ex);
        String errorMessage = trimMessage(ex == null ? null : ex.getMessage());
        Integer currentRetry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetryCount = currentRetry + 1;
        Date nextRetryAt = currentRetry < MAX_RETRY_COUNT ? buildNextRetryAt(nextRetryCount) : null;
        return new FailureUpdate(nextRetryCount, nextRetryAt, errorCode, errorMessage);
    }

    public List<CodeReviewTaskEntity> findRunnableTasks(Date now) {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        wrapper.and(statusWrapper -> statusWrapper
                .eq("status", "PENDING")
                .or(failedWrapper -> failedWrapper
                    .eq("status", "FAILED")
                    .le("retry_count", MAX_RETRY_COUNT)
                    .isNotNull("next_retry_at")
                    .le("next_retry_at", now)))
            .orderByAsc("created_at")
            .last("LIMIT 1");
        return codeReviewTaskMapper.selectList(wrapper);
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

    private static class FailureUpdate {
        private final int nextRetryCount;
        private final Date nextRetryAt;
        private final String errorCode;
        private final String errorMessage;

        private FailureUpdate(int nextRetryCount, Date nextRetryAt, String errorCode, String errorMessage) {
            this.nextRetryCount = nextRetryCount;
            this.nextRetryAt = nextRetryAt;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }

}
