package com.vemo.codereview.review.service;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        if (taskId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            log.info("review task dispatch deferred until transaction commit. taskId={}", taskId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDispatch(taskId);
                }
            });
            return;
        }
        doDispatch(taskId);
    }

    private void doDispatch(Long taskId) {
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
