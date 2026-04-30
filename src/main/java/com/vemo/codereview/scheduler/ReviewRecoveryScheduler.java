package com.vemo.codereview.scheduler;

import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.service.ReviewRetryService;
import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import java.util.Date;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewRecoveryScheduler {

    private final ReviewRetryService reviewRetryService;
    private final ReviewTaskDispatcher reviewTaskDispatcher;

    public ReviewRecoveryScheduler(
        ReviewRetryService reviewRetryService,
        ReviewTaskDispatcher reviewTaskDispatcher) {
        this.reviewRetryService = reviewRetryService;
        this.reviewTaskDispatcher = reviewTaskDispatcher;
    }

    @Scheduled(fixedDelay = 60000L)
    public void recoverPendingTasks() {
        try {
            List<CodeReviewTaskEntity> tasks = reviewRetryService.findRecoverableTasks(new Date());
            for (CodeReviewTaskEntity task : tasks) {
                reviewTaskDispatcher.dispatch(task.getId());
            }
        } catch (RuntimeException ignored) {
            // During startup or isolated tests, the schema may not be ready yet.
        }
    }
}