package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitPayload;
import com.vemo.codereview.platform.gitlab.service.GitLabReviewTargetService;
import com.vemo.codereview.project.model.ProjectCustomReviewBatchRequest;
import com.vemo.codereview.review.entity.CodeReviewBatchEntity;
import com.vemo.codereview.review.entity.CodeReviewBatchTaskRelEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewBatchStoreMapper;
import com.vemo.codereview.review.mapper.ReviewBatchTaskRelStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.service.ProjectCustomReviewBatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:project-custom-review-batch-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.main.allow-bean-definition-overriding=true"
})
@Sql(scripts = "/db/schema.sql")
class ProjectCustomReviewBatchServiceTest {

    @Autowired
    private ProjectCustomReviewBatchService projectCustomReviewBatchService;

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @Autowired
    private ReviewTaskStoreMapper reviewTaskStoreMapper;

    @Autowired
    private ReviewEventStoreMapper reviewEventStoreMapper;

    @Autowired
    private ReviewBatchStoreMapper reviewBatchStoreMapper;

    @Autowired
    private ReviewBatchTaskRelStoreMapper reviewBatchTaskRelStoreMapper;

    @MockBean
    private GitLabReviewTargetService gitLabReviewTargetService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private com.vemo.codereview.project.service.GitLabProjectResolver gitLabProjectResolver;

    @MockBean
    private com.vemo.codereview.review.service.ReviewTaskDispatcher reviewTaskDispatcher;

    @BeforeEach
    void setUp() {
        reviewBatchTaskRelStoreMapper.delete(null);
        reviewBatchStoreMapper.delete(null);
        reviewTaskStoreMapper.delete(null);
        reviewEventStoreMapper.delete(null);
        projectProfileMapper.delete(null);

        when(currentUserService.requireCurrentUserId()).thenReturn(9L);
        when(currentUserService.requireCurrentUserDisplayName()).thenReturn("reviewer");
    }

    @Test
    void shouldSkipReviewedAndRunningTasksAndCreateTaskForNewCommit() {
        ProjectProfileEntity project = insertProject("master release");
        CodeReviewEventEntity reviewedEvent = insertEvent(project, "sha-a", "push", "master");
        CodeReviewTaskEntity reviewedTask = insertTask(reviewedEvent.getId(), project.getId(), "sha-a", "SUCCESS", "Commit A");
        CodeReviewEventEntity runningEvent = insertEvent(project, "sha-b", "push", "release");
        CodeReviewTaskEntity runningTask = insertTask(runningEvent.getId(), project.getId(), "sha-b", "PENDING", "Commit B");

        when(gitLabReviewTargetService.listBranchCommits(
            eq(project.getGitlabProjectUrl()),
            eq(project.getGitlabProjectId()),
            eq("master"),
            any(),
            any(),
            eq(project.getGitlabWebhookToken())))
            .thenReturn(Arrays.asList(buildCommit("sha-a", "Commit A"), buildCommit("sha-b", "Commit B")));
        when(gitLabReviewTargetService.listBranchCommits(
            eq(project.getGitlabProjectUrl()),
            eq(project.getGitlabProjectId()),
            eq("release"),
            any(),
            any(),
            eq(project.getGitlabWebhookToken())))
            .thenReturn(Arrays.asList(buildCommit("sha-b", "Commit B"), buildCommit("sha-c", "Commit C")));

        ProjectCustomReviewBatchRequest request = new ProjectCustomReviewBatchRequest();
        request.setStartTime("2026-05-01 00:00:00");
        request.setEndTime("2026-05-09 23:59:59");
        request.setReviewMode("SKIP_REVIEWED");

        com.vemo.codereview.project.model.ProjectCustomReviewBatchResponse response =
            projectCustomReviewBatchService.createBatch(project, request);

        assertNotNull(response.getBatchId());
        assertEquals(Integer.valueOf(3), response.getTotalCommitCount());
        assertEquals(Integer.valueOf(1), response.getCreatedTaskCount());
        assertEquals(Integer.valueOf(0), response.getRetriedTaskCount());
        assertEquals(Integer.valueOf(1), response.getSkippedReviewedCount());
        assertEquals(Integer.valueOf(1), response.getSkippedRunningCount());
        assertEquals(Integer.valueOf(0), response.getSkippedFailedCount());
        assertEquals(Integer.valueOf(0), response.getFailedCount());

        CodeReviewBatchEntity savedBatch = reviewBatchStoreMapper.selectById(response.getBatchId());
        assertEquals("FINISHED", savedBatch.getStatus());

        List<CodeReviewBatchTaskRelEntity> relations = reviewBatchTaskRelStoreMapper.selectList(null);
        assertEquals(3, relations.size());
        assertEquals(Arrays.asList("CREATED", "SKIPPED_REVIEWED", "SKIPPED_RUNNING"),
            relations.stream().map(CodeReviewBatchTaskRelEntity::getActionType).sorted().collect(Collectors.toList()));

        QueryWrapper<CodeReviewTaskEntity> createdTaskWrapper = new QueryWrapper<CodeReviewTaskEntity>();
        createdTaskWrapper.eq("project_id", project.getId()).eq("target_id", "sha-c");
        CodeReviewTaskEntity createdTask = reviewTaskStoreMapper.selectOne(createdTaskWrapper);
        assertNotNull(createdTask);
        assertEquals("PUSH_REVIEW", createdTask.getTaskType());
        assertEquals(reviewedEvent.getId(), reviewTaskStoreMapper.selectById(reviewedTask.getId()).getEventId());
        assertEquals(runningEvent.getId(), reviewTaskStoreMapper.selectById(runningTask.getId()).getEventId());

        CodeReviewEventEntity createdEvent = reviewEventStoreMapper.selectById(createdTask.getEventId());
        assertNotNull(createdEvent);
        assertEquals("manual_commit_review", createdEvent.getEventType());
        assertEquals("TASK_CREATED", createdEvent.getStatus());
        assertEquals("release", createdEvent.getSubmitBranch());
        assertEquals("author-sha-c", createdEvent.getOperatorName());
        assertEquals("author-sha-c", createdEvent.getOperatorId());
        assertTrue(createdEvent.getPayloadJson().contains("\"user_name\":\"author-sha-c\""));
        assertNotNull(createdEvent.getSubmitTime());

        verify(reviewTaskDispatcher, times(1)).dispatch(createdTask.getId());
    }

    @Test
    void shouldForceRereviewFinishedTasksAndKeepSingleTaskPerCommit() {
        ProjectProfileEntity project = insertProject("master");
        CodeReviewEventEntity successEvent = insertEvent(project, "sha-d", "push", "master");
        CodeReviewTaskEntity successTask = insertTask(successEvent.getId(), project.getId(), "sha-d", "SUCCESS", "Commit D");
        successTask.setRetryCount(3);
        successTask.setErrorCode("OLD");
        successTask.setErrorMessage("old error");
        successTask.setFinishedAt(new Date());
        reviewTaskStoreMapper.updateById(successTask);

        CodeReviewEventEntity failedEvent = insertEvent(project, "sha-e", "push", "master");
        CodeReviewTaskEntity failedTask = insertTask(failedEvent.getId(), project.getId(), "sha-e", "FAILED", "Commit E");
        failedTask.setRetryCount(2);
        failedTask.setErrorCode("ERR");
        failedTask.setErrorMessage("failed");
        failedTask.setFinishedAt(new Date());
        reviewTaskStoreMapper.updateById(failedTask);

        CodeReviewEventEntity pendingEvent = insertEvent(project, "sha-f", "push", "master");
        CodeReviewTaskEntity pendingTask = insertTask(pendingEvent.getId(), project.getId(), "sha-f", "RUNNING", "Commit F");

        when(gitLabReviewTargetService.listBranchCommits(
            eq(project.getGitlabProjectUrl()),
            eq(project.getGitlabProjectId()),
            eq("master"),
            any(),
            any(),
            eq(project.getGitlabWebhookToken())))
            .thenReturn(Arrays.asList(
                buildCommit("sha-d", "Commit D"),
                buildCommit("sha-e", "Commit E"),
                buildCommit("sha-f", "Commit F"),
                buildCommit("sha-g", "Commit G")));

        ProjectCustomReviewBatchRequest request = new ProjectCustomReviewBatchRequest();
        request.setStartTime("2026-05-01 00:00:00");
        request.setEndTime("2026-05-09 23:59:59");
        request.setReviewMode("FORCE_REREVIEW");

        com.vemo.codereview.project.model.ProjectCustomReviewBatchResponse response =
            projectCustomReviewBatchService.createBatch(project, request);

        assertEquals(Integer.valueOf(4), response.getTotalCommitCount());
        assertEquals(Integer.valueOf(1), response.getCreatedTaskCount());
        assertEquals(Integer.valueOf(2), response.getRetriedTaskCount());
        assertEquals(Integer.valueOf(1), response.getSkippedRunningCount());
        assertEquals(Integer.valueOf(0), response.getFailedCount());

        CodeReviewTaskEntity savedSuccessTask = reviewTaskStoreMapper.selectById(successTask.getId());
        CodeReviewTaskEntity savedFailedTask = reviewTaskStoreMapper.selectById(failedTask.getId());
        assertEquals("PENDING", savedSuccessTask.getStatus());
        assertEquals(Integer.valueOf(0), savedSuccessTask.getRetryCount());
        assertEquals(null, savedSuccessTask.getErrorCode());
        assertEquals("PENDING", savedFailedTask.getStatus());
        assertEquals(Integer.valueOf(0), savedFailedTask.getRetryCount());
        assertEquals(null, savedFailedTask.getErrorCode());

        QueryWrapper<CodeReviewTaskEntity> newTaskWrapper = new QueryWrapper<CodeReviewTaskEntity>();
        newTaskWrapper.eq("project_id", project.getId()).eq("target_id", "sha-g");
        CodeReviewTaskEntity createdTask = reviewTaskStoreMapper.selectOne(newTaskWrapper);
        assertNotNull(createdTask);
        assertEquals("RUNNING", reviewTaskStoreMapper.selectById(pendingTask.getId()).getStatus());

        List<CodeReviewTaskEntity> allTasks = reviewTaskStoreMapper.selectList(new QueryWrapper<CodeReviewTaskEntity>().eq("project_id", project.getId()));
        assertEquals(4, allTasks.size());

        verify(reviewTaskDispatcher, times(3)).dispatch(any(Long.class));
    }

    @Test
    void shouldUseRequestedReviewBranchesWithinConfiguredScope() {
        ProjectProfileEntity project = insertProject("master release hotfix");

        when(gitLabReviewTargetService.listBranchCommits(
            eq(project.getGitlabProjectUrl()),
            eq(project.getGitlabProjectId()),
            eq("release"),
            any(),
            any(),
            eq(project.getGitlabWebhookToken())))
            .thenReturn(Arrays.asList(buildCommit("sha-r1", "Release Commit")));

        ProjectCustomReviewBatchRequest request = new ProjectCustomReviewBatchRequest();
        request.setStartTime("2026-05-01 00:00:00");
        request.setEndTime("2026-05-09 23:59:59");
        request.setReviewMode("SKIP_REVIEWED");
        request.setReviewBranches(Arrays.asList("release"));

        com.vemo.codereview.project.model.ProjectCustomReviewBatchResponse response =
            projectCustomReviewBatchService.createBatch(project, request);

        assertEquals(Integer.valueOf(1), response.getTotalCommitCount());
        CodeReviewBatchEntity savedBatch = reviewBatchStoreMapper.selectById(response.getBatchId());
        assertEquals("release", savedBatch.getBranchScope());

        List<CodeReviewBatchTaskRelEntity> relations = reviewBatchTaskRelStoreMapper.selectList(null);
        assertEquals(1, relations.size());
        assertEquals("release", relations.get(0).getSubmitBranch());

        verify(gitLabReviewTargetService, times(1)).listBranchCommits(
            eq(project.getGitlabProjectUrl()),
            eq(project.getGitlabProjectId()),
            eq("release"),
            any(),
            any(),
            eq(project.getGitlabWebhookToken()));
        verify(reviewTaskDispatcher, times(1)).dispatch(any(Long.class));
    }

    @Test
    void shouldRejectRequestedBranchOutsideConfiguredScope() {
        ProjectProfileEntity project = insertProject("master release");

        ProjectCustomReviewBatchRequest request = new ProjectCustomReviewBatchRequest();
        request.setStartTime("2026-05-01 00:00:00");
        request.setEndTime("2026-05-09 23:59:59");
        request.setReviewMode("SKIP_REVIEWED");
        request.setReviewBranches(Arrays.asList("hotfix"));

        try {
            projectCustomReviewBatchService.createBatch(project, request);
        } catch (com.vemo.codereview.common.exception.DomainException ex) {
            assertEquals("CUSTOM_REVIEW_BRANCH_SCOPE_INVALID", ex.getCode());
            return;
        }
        fail("Expected custom review branch validation failure");
    }

    private ProjectProfileEntity insertProject(String reviewBranches) {
        Date now = new Date();
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("project:test");
        entity.setProjectName("MAS Core");
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(1001L);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/mas-core");
        entity.setGitlabWebhookToken("project-token");
        entity.setReviewBranches(reviewBranches);
        entity.setOwnerUserId(2L);
        entity.setAiReviewEnabled(true);
        entity.setReviewContextEnabled(true);
        entity.setGitlabNoteEnabled(true);
        entity.setWecomNotifyEnabled(false);
        entity.setActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        projectProfileMapper.insert(entity);
        return entity;
    }

    private CodeReviewEventEntity insertEvent(ProjectProfileEntity project, String objectId, String eventType, String submitBranch) {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType(eventType);
        event.setProjectId(project.getId());
        event.setProjectName(project.getProjectName());
        event.setObjectId(objectId);
        event.setObjectType("commit");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setSubmitBranch(submitBranch);
        event.setSubmitTime(now);
        event.setIdempotentKey("event-" + objectId + "-" + now.getTime());
        event.setPayloadJson("{\"object_kind\":\"push\"}");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        reviewEventStoreMapper.insert(event);
        return event;
    }

    private CodeReviewTaskEntity insertTask(Long eventId, Long projectId, String targetId, String status, String title) {
        Date now = new Date();
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(eventId);
        task.setTaskType("PUSH_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(projectId);
        task.setTargetId(targetId);
        task.setTargetTitle(title);
        task.setStatus(status);
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        reviewTaskStoreMapper.insert(task);
        return task;
    }

    private GitLabCommitPayload buildCommit(String sha, String title) {
        GitLabCommitPayload payload = new GitLabCommitPayload();
        payload.setId(sha);
        payload.setTitle(title);
        payload.setMessage(title + "\n\nbody");
        payload.setAuthorName("author-" + sha);
        payload.setCommittedDate("2026-05-09T10:00:00+08:00");
        return payload;
    }
}
