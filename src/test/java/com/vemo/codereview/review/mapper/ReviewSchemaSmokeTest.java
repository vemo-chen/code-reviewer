package com.vemo.codereview.review.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.review.entity.CodeReviewBatchEntity;
import com.vemo.codereview.review.entity.CodeReviewBatchTaskRelEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:reviewdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",    "spring.main.allow-bean-definition-overriding=true"
})
@Sql(scripts = "/db/schema.sql")
class ReviewSchemaSmokeTest {

    @Autowired
    private ReviewEventStoreMapper codeReviewEventMapper;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @Autowired
    private ReviewBatchStoreMapper codeReviewBatchMapper;

    @Autowired
    private ReviewBatchTaskRelStoreMapper codeReviewBatchTaskRelMapper;

    @Test
    void shouldInsertAndQueryReviewEntities() {
        Date now = new Date();

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("mr-1");
        event.setObjectType("merge_request");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setSubmitTime(now);
        event.setMrState("OPEN");
        event.setMrHeadSha("head-a");
        event.setMergedSha(null);
        event.setIdempotentKey("gitlab-1001-merge_request-mr-1");
        event.setPayloadJson("{\"object_kind\":\"merge_request\"}");
        event.setStatus("PENDING");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        assertEquals(1, codeReviewEventMapper.insert(event));
        assertNotNull(event.getId());

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("1");
        task.setTargetTitle("Initial merge request");
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        assertEquals(1, codeReviewTaskMapper.insert(task));
        assertNotNull(task.getId());

        CodeReviewEventEntity savedEvent = codeReviewEventMapper.selectById(event.getId());
        CodeReviewTaskEntity savedTask = codeReviewTaskMapper.selectById(task.getId());

        assertEquals("gitlab", savedEvent.getSourcePlatform());
        assertNotNull(savedEvent.getSubmitTime());
        assertEquals("OPEN", savedEvent.getMrState());
        assertEquals("head-a", savedEvent.getMrHeadSha());
        assertNull(savedEvent.getMergedSha());
        assertEquals(event.getId(), savedTask.getEventId());
        assertEquals("MR_REVIEW", savedTask.getTaskType());

        CodeReviewBatchEntity batch = new CodeReviewBatchEntity();
        batch.setProjectId(1001L);
        batch.setTriggerType("CUSTOM_REVIEW");
        batch.setReviewMode("SKIP_REVIEWED");
        batch.setStartTime(now);
        batch.setEndTime(now);
        batch.setBranchScope("master,release");
        batch.setStatus("RUNNING");
        batch.setCreatedBy(2001L);
        batch.setCreatedByName("bob");
        batch.setTotalCommitCount(1);
        batch.setCreatedTaskCount(1);
        batch.setRetriedTaskCount(0);
        batch.setSkippedReviewedCount(0);
        batch.setSkippedRunningCount(0);
        batch.setSkippedFailedCount(0);
        batch.setFailedCount(0);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        assertEquals(1, codeReviewBatchMapper.insert(batch));
        assertNotNull(batch.getId());

        CodeReviewBatchTaskRelEntity rel = new CodeReviewBatchTaskRelEntity();
        rel.setBatchId(batch.getId());
        rel.setTaskId(task.getId());
        rel.setTargetId("1");
        rel.setSubmitBranch("master");
        rel.setActionType("CREATED");
        rel.setMessage("Task created from custom review batch");
        rel.setCreatedAt(now);
        rel.setUpdatedAt(now);
        assertEquals(1, codeReviewBatchTaskRelMapper.insert(rel));
        assertNotNull(rel.getId());

        CodeReviewBatchEntity savedBatch = codeReviewBatchMapper.selectById(batch.getId());
        CodeReviewBatchTaskRelEntity savedRel = codeReviewBatchTaskRelMapper.selectById(rel.getId());

        assertEquals("CUSTOM_REVIEW", savedBatch.getTriggerType());
        assertEquals(batch.getId(), savedRel.getBatchId());
        assertEquals(task.getId(), savedRel.getTaskId());
    }

    @Test
    void shouldAllowMultipleMergeRequestTasksWithSameTargetId() {
        Date now = new Date();

        CodeReviewEventEntity firstEvent = buildMergeRequestEvent("mr-event-1", now);
        assertEquals(1, codeReviewEventMapper.insert(firstEvent));

        CodeReviewTaskEntity firstTask = buildMergeRequestTask(firstEvent.getId(), "1", "Initial merge request", now);
        assertEquals(1, codeReviewTaskMapper.insert(firstTask));

        CodeReviewEventEntity secondEvent = buildMergeRequestEvent("mr-event-2", now);
        assertEquals(1, codeReviewEventMapper.insert(secondEvent));

        CodeReviewTaskEntity secondTask = buildMergeRequestTask(secondEvent.getId(), "1", "Updated merge request", now);
        assertEquals(1, codeReviewTaskMapper.insert(secondTask));

        assertNotNull(firstTask.getId());
        assertNotNull(secondTask.getId());
    }

    private CodeReviewEventEntity buildMergeRequestEvent(String idempotentKey, Date now) {
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId(idempotentKey);
        event.setObjectType("merge_request");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setSubmitTime(now);
        event.setIdempotentKey(idempotentKey);
        event.setPayloadJson("{\"object_kind\":\"merge_request\"}");
        event.setStatus("PENDING");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private CodeReviewTaskEntity buildMergeRequestTask(Long eventId, String targetId, String targetTitle, Date now) {
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(eventId);
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId(targetId);
        task.setTargetTitle(targetTitle);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
