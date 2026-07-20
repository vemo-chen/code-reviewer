package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.service.ReviewStateService;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:review-state-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class ReviewStateServiceTest {

    @Autowired
    private ReviewStateService reviewStateService;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @BeforeEach
    void clearData() {
        codeReviewTaskMapper.delete(null);
    }

    @Test
    void shouldClaimPendingTask() {
        CodeReviewTaskEntity task = insertTask("PENDING", 0, null);

        assertTrue(reviewStateService.claimTaskRunning(task.getId(), "token-1"));

        CodeReviewTaskEntity claimedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("RUNNING", claimedTask.getStatus());
        assertEquals("token-1", claimedTask.getExecutionToken());
    }

    @Test
    void shouldClaimFailedTaskOnlyWhenRetryWindowArrives() {
        CodeReviewTaskEntity futureRetryTask = insertTask("FAILED", 1, minutesFromNow(1));
        CodeReviewTaskEntity exhaustedRetryTask = insertTask("FAILED", 4, minutesFromNow(-1));
        CodeReviewTaskEntity runnableRetryTask = insertTask("FAILED", 2, minutesFromNow(-1));
        CodeReviewTaskEntity thirdRetryTask = insertTask("FAILED", 3, minutesFromNow(-1));

        assertFalse(reviewStateService.claimTaskRunning(futureRetryTask.getId(), "future-token"));
        assertFalse(reviewStateService.claimTaskRunning(exhaustedRetryTask.getId(), "exhausted-token"));
        assertTrue(reviewStateService.claimTaskRunning(runnableRetryTask.getId(), "retry-token"));
        assertTrue(reviewStateService.claimTaskRunning(thirdRetryTask.getId(), "third-retry-token"));
    }

    @Test
    void shouldInterruptRunningTaskAndClearExecutionToken() {
        CodeReviewTaskEntity task = insertTask("RUNNING", 0, null);
        task.setExecutionToken("running-token");
        codeReviewTaskMapper.updateById(task);

        assertTrue(reviewStateService.interruptRunningTask(task.getId()));

        CodeReviewTaskEntity interruptedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("FAILED", interruptedTask.getStatus());
        assertEquals("USER_INTERRUPTED", interruptedTask.getErrorCode());
        assertNull(interruptedTask.getExecutionToken());
        assertNull(interruptedTask.getNextRetryAt());
    }

    @Test
    void shouldIgnoreFailureFromInvalidExecutionToken() {
        CodeReviewTaskEntity task = insertTask("RUNNING", 0, null);
        task.setExecutionToken("current-token");
        codeReviewTaskMapper.updateById(task);

        assertFalse(reviewStateService.markTaskFailedIfCurrent(
            task, "old-token", 1, minutesFromNow(1), "LLM_ERROR", "timeout"));

        CodeReviewTaskEntity unchangedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("RUNNING", unchangedTask.getStatus());
        assertEquals("current-token", unchangedTask.getExecutionToken());
        assertNull(unchangedTask.getErrorCode());
    }

    @Test
    void shouldClearExecutionTokenWhenCurrentFailureIsRecorded() {
        CodeReviewTaskEntity task = insertTask("RUNNING", 0, null);
        task.setExecutionToken("current-token");
        codeReviewTaskMapper.updateById(task);

        assertTrue(reviewStateService.markTaskFailedIfCurrent(
            task, "current-token", 1, minutesFromNow(1), "LLM_ERROR", "timeout"));

        CodeReviewTaskEntity failedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("FAILED", failedTask.getStatus());
        assertNull(failedTask.getExecutionToken());
    }

    @Test
    void shouldMarkRunningTaskFailedByCurrentExecutionToken() {
        CodeReviewTaskEntity task = insertTask("RUNNING", 0, null);
        task.setExecutionToken("current-token");
        codeReviewTaskMapper.updateById(task);

        assertTrue(reviewStateService.markRunningTaskFailedIfCurrent(
            task.getId(), "current-token", "REVIEW_WORKER_FATAL_ERROR", "Java heap space"));

        CodeReviewTaskEntity failedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("FAILED", failedTask.getStatus());
        assertEquals("REVIEW_WORKER_FATAL_ERROR", failedTask.getErrorCode());
        assertEquals("Java heap space", failedTask.getErrorMessage());
        assertNull(failedTask.getExecutionToken());
        assertNull(failedTask.getNextRetryAt());
    }

    @Test
    void shouldIgnoreFatalFailureFromInvalidExecutionToken() {
        CodeReviewTaskEntity task = insertTask("RUNNING", 0, null);
        task.setExecutionToken("current-token");
        codeReviewTaskMapper.updateById(task);

        assertFalse(reviewStateService.markRunningTaskFailedIfCurrent(
            task.getId(), "old-token", "REVIEW_WORKER_FATAL_ERROR", "Java heap space"));

        CodeReviewTaskEntity unchangedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("RUNNING", unchangedTask.getStatus());
        assertEquals("current-token", unchangedTask.getExecutionToken());
        assertNull(unchangedTask.getErrorCode());
    }

    private CodeReviewTaskEntity insertTask(String status, int retryCount, Date nextRetryAt) {
        Date now = new Date();
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(1L);
        task.setTaskType("PUSH_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("abcdef123456");
        task.setTargetTitle("Review task");
        task.setStatus(status);
        task.setRetryCount(retryCount);
        task.setNextRetryAt(nextRetryAt);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);
        return task;
    }

    private Date minutesFromNow(int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }
}
