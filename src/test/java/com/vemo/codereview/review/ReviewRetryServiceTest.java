package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.service.ReviewRetryService;
import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import com.vemo.codereview.scheduler.ReviewRecoveryScheduler;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:review-retry-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class ReviewRetryServiceTest {

    @Autowired
    private ReviewRetryService reviewRetryService;

    @Autowired
    private ReviewRecoveryScheduler reviewRecoveryScheduler;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @Autowired
    private ReviewEventStoreMapper codeReviewEventMapper;

    @MockBean
    private ReviewTaskDispatcher reviewTaskDispatcher;

    @Test
    void shouldMarkTaskFailedAndDispatchWhenRetryWindowArrives() {
        Date now = new Date();

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(1L);
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("7");
        task.setTargetTitle("Add review pipeline");
        task.setStatus("RUNNING");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);

        reviewRetryService.handleFailure(task, new DomainException("LLM_IO_ERROR", "Model timeout"));

        CodeReviewTaskEntity failedTask = codeReviewTaskMapper.selectById(task.getId());
        assertEquals("FAILED", failedTask.getStatus());
        assertEquals(Integer.valueOf(1), failedTask.getRetryCount());
        assertEquals("LLM_IO_ERROR", failedTask.getErrorCode());
        assertNotNull(failedTask.getNextRetryAt());

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, -1);
        failedTask.setNextRetryAt(calendar.getTime());
        codeReviewTaskMapper.updateById(failedTask);

        reviewRecoveryScheduler.recoverPendingTasks();

        verify(reviewTaskDispatcher, times(1)).dispatch(task.getId());
    }

    @Test
    void shouldRecoverOrphanPendingTask() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, -11);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(2L);
        task.setTaskType("PUSH_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("abcdef123456");
        task.setTargetTitle("Add push review");
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedAt(calendar.getTime());
        task.setUpdatedAt(calendar.getTime());
        codeReviewTaskMapper.insert(task);

        reviewRecoveryScheduler.recoverPendingTasks();

        verify(reviewTaskDispatcher, times(1)).dispatch(task.getId());
    }

    @Test
    void shouldKeepTaskFailedAndMarkEventFailedAfterMaxRetries() {
        Date now = new Date();

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("push");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("abcdef123456");
        event.setObjectType("commit");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setIdempotentKey("retry-terminal-failure");
        event.setPayloadJson("{}");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        codeReviewEventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("PUSH_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("abcdef123456");
        task.setTargetTitle("Add push review");
        task.setStatus("RUNNING");
        task.setRetryCount(3);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);

        reviewRetryService.handleFailure(task, new DomainException("GITLAB_RESPONSE_PARSE_ERROR", "html page returned"));

        CodeReviewTaskEntity failedTask = codeReviewTaskMapper.selectById(task.getId());
        CodeReviewEventEntity failedEvent = codeReviewEventMapper.selectById(event.getId());

        assertEquals("FAILED", failedTask.getStatus());
        assertEquals(Integer.valueOf(4), failedTask.getRetryCount());
        assertEquals("FAILED", failedEvent.getStatus());
        assertEquals("GITLAB_RESPONSE_PARSE_ERROR", failedTask.getErrorCode());
    }

    @Test
    void shouldKeepEventFailedWhenTerminalFailureIsHandledAgain() {
        Date now = new Date();

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("push");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("abcdef123456");
        event.setObjectType("commit");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setIdempotentKey("retry-terminal-failure-idempotent");
        event.setPayloadJson("{}");
        event.setStatus("FAILED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        codeReviewEventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("PUSH_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("abcdef123456");
        task.setTargetTitle("Add push review");
        task.setStatus("RUNNING");
        task.setRetryCount(4);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);

        reviewRetryService.handleFailure(task, new DomainException("REVIEW_RESULT_PARSE_ERROR", "parse failed"));

        CodeReviewTaskEntity failedTask = codeReviewTaskMapper.selectById(task.getId());
        CodeReviewEventEntity failedEvent = codeReviewEventMapper.selectById(event.getId());

        assertEquals("FAILED", failedTask.getStatus());
        assertEquals(Integer.valueOf(5), failedTask.getRetryCount());
        assertEquals("FAILED", failedEvent.getStatus());
        assertEquals("REVIEW_RESULT_PARSE_ERROR", failedTask.getErrorCode());
    }
}
