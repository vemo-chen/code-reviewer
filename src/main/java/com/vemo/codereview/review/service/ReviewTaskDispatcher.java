package com.vemo.codereview.review.service;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReviewTaskDispatcher {

    private final Executor reviewTaskExecutor;
    private final ReviewTaskWorker reviewTaskWorker;

    public ReviewTaskDispatcher(
        @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor,
        ReviewTaskWorker reviewTaskWorker) {
        this.reviewTaskExecutor = reviewTaskExecutor;
        this.reviewTaskWorker = reviewTaskWorker;
    }

    public void dispatch(Long taskId) {
        long queuedNs = System.nanoTime();
        log.info("review task queued. taskId={}", taskId);
        reviewTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                log.info("review task dequeued. taskId={}, queueWaitMs={}", taskId, elapsedMs(queuedNs));
                reviewTaskWorker.process(taskId);
            }
        });
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
