package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.service.ReviewTaskDispatcher;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CodeReviewerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:webhookdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "code-reviewer.gitlab.token=test-gitlab-token"
})
@Sql(scripts = "/db/schema.sql")
class GitLabWebhookControllerTest {

    @MockBean
    private ReviewTaskDispatcher reviewTaskDispatcher;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewEventStoreMapper codeReviewEventMapper;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @BeforeEach
    void setUp() {
        codeReviewTaskMapper.delete(new QueryWrapper<CodeReviewTaskEntity>());
        codeReviewEventMapper.delete(new QueryWrapper<CodeReviewEventEntity>());
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        insertProject(1001L, true, true);
        insertProject(1002L, true, true);
    }

    @Test
    void shouldAcceptMergeRequestWebhookAndCreateTaskCreatedEvent() throws Exception {
        String payload = "{"
            + "\"object_kind\":\"merge_request\","
            + "\"event_type\":\"merge_request\","
            + "\"user\":{\"id\":12,\"name\":\"alice\"},"
            + "\"project\":{\"id\":1001,\"name\":\"code-reviewer\"},"
            + "\"object_attributes\":{"
            + "\"id\":501,"
            + "\"iid\":7,"
            + "\"title\":\"Add review pipeline\","
            + "\"action\":\"open\","
            + "\"source_branch\":\"feature/review\","
            + "\"target_branch\":\"main\","
            + "\"last_commit\":{\"id\":\"abcdef123456\"}"
            + "}"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("accepted"));

        CodeReviewEventEntity event = codeReviewEventMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "501"));
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "7"));

        assertNotNull(event);
        assertNotNull(task);
        assertEquals("TASK_CREATED", event.getStatus());
        assertEquals("PENDING", task.getStatus());
        assertEquals(event.getId(), task.getEventId());
        assertEquals("MR_REVIEW", task.getTaskType());
    }

    @Test
    void shouldAcceptPushWebhookAndCreatePendingPushTask() throws Exception {
        String payload = "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/feature/review\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"abcdef123456\","
            + "\"project\":{\"id\":1002,\"name\":\"code-reviewer\"},"
            + "\"commits\":[{\"id\":\"abcdef123456\",\"message\":\"Add push review\",\"title\":\"Add push review\"}]"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("accepted"));

        CodeReviewEventEntity event = codeReviewEventMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "abcdef123456"));
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "abcdef123456"));

        assertNotNull(event);
        assertNotNull(task);
        assertEquals("TASK_CREATED", event.getStatus());
        assertEquals("commit", event.getObjectType());
        assertEquals("abcdef123456", task.getTargetId());
        assertEquals("PUSH_REVIEW", task.getTaskType());
        assertEquals("Add push review", task.getTargetTitle());
    }

    @Test
    void shouldIgnoreWebhookWhenBranchIsNotConfiguredForReview() throws Exception {
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        insertProject(1003L, true, true, "main,release");
        String payload = "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/feature/noise\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"abcdef123456\","
            + "\"project\":{\"id\":1003,\"name\":\"code-reviewer\"},"
            + "\"commits\":[{\"id\":\"abcdef123456\",\"message\":\"Noise change\",\"title\":\"Noise change\"}]"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        CodeReviewEventEntity event = codeReviewEventMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "abcdef123456"));
        Long taskCount = codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertNotNull(event);
        assertEquals("IGNORED", event.getStatus());
        assertEquals(Long.valueOf(0L), taskCount);
    }

    @Test
    void shouldCreateTaskWhenBranchIsConfiguredForReview() throws Exception {
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        insertProject(1004L, true, true, "main,feature/review");
        String payload = "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/feature/review\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"fedcba654321\","
            + "\"project\":{\"id\":1004,\"name\":\"code-reviewer\"},"
            + "\"commits\":[{\"id\":\"fedcba654321\",\"message\":\"Review change\",\"title\":\"Review change\"}]"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        CodeReviewTaskEntity task = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "fedcba654321"));

        assertNotNull(task);
        assertEquals("PUSH_REVIEW", task.getTaskType());
    }

    @Test
    void shouldReviewOnlyNonMergeCommitsInMultiCommitPush() throws Exception {
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        insertProject(1005L, true, true, "feature/review");
        String payload = "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/feature/review\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"merge33333333\","
            + "\"project\":{\"id\":1005,\"name\":\"code-reviewer\"},"
            + "\"commits\":["
            + "{\"id\":\"normal11111111\",\"message\":\"Add service\",\"title\":\"Add service\"},"
            + "{\"id\":\"normal22222222\",\"message\":\"Fix controller\",\"title\":\"Fix controller\"},"
            + "{\"id\":\"merge33333333\",\"message\":\"Merge branch 'main' into feature/review\",\"title\":\"Merge branch 'main' into feature/review\"}"
            + "]"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        Long eventCount = codeReviewEventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>());
        Long taskCount = codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());
        CodeReviewTaskEntity firstTask = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "normal11111111"));
        CodeReviewTaskEntity secondTask = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "normal22222222"));
        CodeReviewTaskEntity mergeTask = codeReviewTaskMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "merge33333333"));

        assertEquals(Long.valueOf(2L), eventCount);
        assertEquals(Long.valueOf(2L), taskCount);
        assertNotNull(firstTask);
        assertNotNull(secondTask);
        assertNull(mergeTask);
    }

    @Test
    void shouldIgnorePushWhenAllCommitsAreMergeCommits() throws Exception {
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        insertProject(1006L, true, true, "feature/review");
        String payload = "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/feature/review\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"merge22222222\","
            + "\"project\":{\"id\":1006,\"name\":\"code-reviewer\"},"
            + "\"commits\":["
            + "{\"id\":\"merge11111111\",\"message\":\"Merge branch 'main' into feature/review\",\"title\":\"Merge branch 'main' into feature/review\"},"
            + "{\"id\":\"merge22222222\",\"message\":\"Merge remote-tracking branch 'origin/main'\",\"title\":\"Merge remote-tracking branch 'origin/main'\"}"
            + "]"
            + "}";

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        CodeReviewEventEntity event = codeReviewEventMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "merge22222222"));
        Long taskCount = codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertNotNull(event);
        assertEquals("IGNORED", event.getStatus());
        assertEquals(Long.valueOf(0L), taskCount);
    }

    private void insertProject(Long gitlabProjectId, boolean active, boolean aiReviewEnabled) {
        insertProject(gitlabProjectId, active, aiReviewEnabled, null);
    }

    private void insertProject(Long gitlabProjectId, boolean active, boolean aiReviewEnabled, String reviewBranches) {
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("project:" + gitlabProjectId);
        entity.setProjectName("Project " + gitlabProjectId);
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(gitlabProjectId);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/project-" + gitlabProjectId);
        entity.setReviewBranches(reviewBranches);
        entity.setAiReviewEnabled(aiReviewEnabled);
        entity.setGitlabNoteEnabled(true);
        entity.setWecomNotifyEnabled(false);
        entity.setActive(active);
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        projectProfileMapper.insert(entity);
    }
}
