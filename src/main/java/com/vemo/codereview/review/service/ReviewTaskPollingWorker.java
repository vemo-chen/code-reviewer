package com.vemo.codereview.review.service;

import com.vemo.codereview.common.config.AppProperties;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReviewTaskPollingWorker implements SmartInitializingSingleton {

    private final ReviewRetryService reviewRetryService;
    private final ReviewStateService reviewStateService;
    private final ReviewTaskWorker reviewTaskWorker;
    private final Executor reviewTaskExecutor;
    private final AppProperties appProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object claimLock = new Object();

    public ReviewTaskPollingWorker(
        ReviewRetryService reviewRetryService,
        ReviewStateService reviewStateService,
        ReviewTaskWorker reviewTaskWorker,
        @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor,
        AppProperties appProperties) {
        this.reviewRetryService = reviewRetryService;
        this.reviewStateService = reviewStateService;
        this.reviewTaskWorker = reviewTaskWorker;
        this.reviewTaskExecutor = reviewTaskExecutor;
        this.appProperties = appProperties == null ? new AppProperties() : appProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        AppProperties.ReviewWorker workerProperties = appProperties.getReviewWorker();
        if (workerProperties == null || !workerProperties.isEnabled()) {
            log.info("database review task polling worker disabled.");
            return;
        }
        startWorkers(workerProperties);
    }

    public void stop() {
        running.set(false);
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    public boolean runOnce() {
        ClaimedTask claimedTask = claimNextTask();
        if (claimedTask == null) {
            return false;
        }
        try {
            reviewTaskWorker.processClaimed(claimedTask.taskId, claimedTask.executionToken);
        } catch (RuntimeException ex) {
            log.warn("database review task execution failed. taskId={}, message={}",
                claimedTask.taskId, ex.getMessage());
        } catch (Throwable error) {
            markFatalTaskFailure(claimedTask, error);
        }
        return true;
    }

    private ClaimedTask claimNextTask() {
        synchronized (claimLock) {
            List<CodeReviewTaskEntity> tasks = reviewRetryService.findRunnableTasks(new Date());
            if (tasks == null || tasks.isEmpty()) {
                return null;
            }
            return claimFirstRunnableTask(tasks);
        }
    }

    private ClaimedTask claimFirstRunnableTask(List<CodeReviewTaskEntity> tasks) {
        for (CodeReviewTaskEntity task : tasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            String executionToken = UUID.randomUUID().toString();
            if (reviewStateService.claimTaskRunning(task.getId(), executionToken)) {
                return new ClaimedTask(task.getId(), executionToken);
            }
        }
        return null;
    }

    private void markFatalTaskFailure(ClaimedTask claimedTask, Throwable error) {
        String message = trimMessage(error == null ? null : error.getMessage());
        log.error("database review task execution crashed. taskId={}, errorType={}, message={}",
            claimedTask.taskId, error == null ? null : error.getClass().getName(), message);
        boolean updated = reviewStateService.markRunningTaskFailedIfCurrent(
            claimedTask.taskId, claimedTask.executionToken, "REVIEW_WORKER_FATAL_ERROR", message);
        if (!updated) {
            log.info("database review task fatal failure ignored because task is no longer current. taskId={}",
                claimedTask.taskId);
        }
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private void startWorkers(AppProperties.ReviewWorker workerProperties) {
        if (reviewTaskExecutor == null) {
            log.warn("database review task polling worker not started because reviewTaskExecutor is unavailable.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int workerCount = Math.max(1, workerProperties.getWorkerCount());
        for (int i = 0; i < workerCount; i++) {
            final int workerIndex = i + 1;
            reviewTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    runLoop(workerIndex);
                }
            });
        }
        log.info("database review task polling workers started. workerCount={}", workerCount);
    }

    private void runLoop(int workerIndex) {
        AppProperties.ReviewWorker workerProperties = appProperties.getReviewWorker();
        long idleSleepMs = Math.max(100L, workerProperties.getIdleSleepMs());
        long errorSleepMs = Math.max(100L, workerProperties.getErrorSleepMs());
        log.info("database review task polling worker running. workerIndex={}", workerIndex);
        while (running.get()) {
            try {
                boolean processed = runOnce();
                if (!processed) {
                    sleep(idleSleepMs);
                }
            } catch (RuntimeException ex) {
                log.warn("database review task polling worker iteration failed. workerIndex={}, message={}",
                    workerIndex, ex.getMessage());
                sleep(errorSleepMs);
            }
        }
        log.info("database review task polling worker stopped. workerIndex={}", workerIndex);
    }

    private void sleep(long sleepMs) {
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private static class ClaimedTask {
        private final Long taskId;
        private final String executionToken;

        private ClaimedTask(Long taskId, String executionToken) {
            this.taskId = taskId;
            this.executionToken = executionToken;
        }
    }
}
