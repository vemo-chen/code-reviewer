package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.service.ReviewRetryService;
import com.vemo.codereview.review.service.ReviewStateService;
import com.vemo.codereview.review.service.ReviewTaskPollingWorker;
import com.vemo.codereview.review.service.ReviewTaskWorker;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReviewTaskPollingWorkerTest {

    @Test
    void shouldPollDatabaseRunnableTasksAndRunOneTaskDirectly() {
        ReviewRetryService reviewRetryService = Mockito.mock(ReviewRetryService.class);
        ReviewStateService reviewStateService = Mockito.mock(ReviewStateService.class);
        ReviewTaskWorker reviewTaskWorker = Mockito.mock(ReviewTaskWorker.class);
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setId(31L);
        when(reviewRetryService.findRunnableTasks(any(Date.class))).thenReturn(Arrays.asList(task));
        when(reviewStateService.claimTaskRunning(Mockito.eq(31L), anyString())).thenReturn(true);

        ReviewTaskPollingWorker pollingWorker = new ReviewTaskPollingWorker(
            reviewRetryService,
            reviewStateService,
            reviewTaskWorker,
            null,
            null
        );

        assertTrue(pollingWorker.runOnce());
        verify(reviewStateService).claimTaskRunning(Mockito.eq(31L), anyString());
        verify(reviewTaskWorker).processClaimed(Mockito.eq(31L), anyString());
    }

    @Test
    void shouldKeepPollingLoopAliveWhenOneTaskFails() {
        ReviewRetryService reviewRetryService = Mockito.mock(ReviewRetryService.class);
        ReviewStateService reviewStateService = Mockito.mock(ReviewStateService.class);
        ReviewTaskWorker reviewTaskWorker = Mockito.mock(ReviewTaskWorker.class);
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setId(32L);
        when(reviewRetryService.findRunnableTasks(any(Date.class))).thenReturn(Arrays.asList(task));
        when(reviewStateService.claimTaskRunning(Mockito.eq(32L), anyString())).thenReturn(true);
        doThrow(new RuntimeException("model timeout")).when(reviewTaskWorker).processClaimed(Mockito.eq(32L), anyString());

        ReviewTaskPollingWorker pollingWorker = new ReviewTaskPollingWorker(
            reviewRetryService,
            reviewStateService,
            reviewTaskWorker,
            null,
            null
        );

        assertTrue(pollingWorker.runOnce());
        verify(reviewTaskWorker).processClaimed(Mockito.eq(32L), anyString());
    }

    @Test
    void shouldMarkClaimedTaskFailedAndKeepPollingWhenWorkerThrowsError() {
        ReviewRetryService reviewRetryService = Mockito.mock(ReviewRetryService.class);
        ReviewStateService reviewStateService = Mockito.mock(ReviewStateService.class);
        ReviewTaskWorker reviewTaskWorker = Mockito.mock(ReviewTaskWorker.class);
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setId(33L);
        when(reviewRetryService.findRunnableTasks(any(Date.class))).thenReturn(Arrays.asList(task));
        when(reviewStateService.claimTaskRunning(Mockito.eq(33L), anyString())).thenReturn(true);
        when(reviewStateService.markRunningTaskFailedIfCurrent(
            Mockito.eq(33L),
            anyString(),
            Mockito.eq("REVIEW_WORKER_FATAL_ERROR"),
            Mockito.eq("Java heap space"))).thenReturn(true);
        doThrow(new OutOfMemoryError("Java heap space"))
            .when(reviewTaskWorker).processClaimed(Mockito.eq(33L), anyString());

        ReviewTaskPollingWorker pollingWorker = new ReviewTaskPollingWorker(
            reviewRetryService,
            reviewStateService,
            reviewTaskWorker,
            null,
            null
        );

        assertTrue(pollingWorker.runOnce());
        verify(reviewStateService).markRunningTaskFailedIfCurrent(
            Mockito.eq(33L),
            anyString(),
            Mockito.eq("REVIEW_WORKER_FATAL_ERROR"),
            Mockito.eq("Java heap space"));
    }
}
