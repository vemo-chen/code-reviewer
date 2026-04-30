package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = CodeReviewerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:webhook-project-routing-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class GitLabWebhookProjectRoutingTest {

    @MockBean
    private ReviewTaskDispatcher reviewTaskDispatcher;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewEventStoreMapper reviewEventStoreMapper;

    @Autowired
    private ReviewTaskStoreMapper reviewTaskStoreMapper;

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @BeforeEach
    void clearData() {
        reviewTaskStoreMapper.delete(new QueryWrapper<CodeReviewTaskEntity>());
        reviewEventStoreMapper.delete(new QueryWrapper<CodeReviewEventEntity>());
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
    }

    @Test
    void shouldRejectWebhookWhenProjectIsNotManaged() throws Exception {
        invokeMergeRequestWebhook(9001L, status().isBadRequest());

        CodeReviewEventEntity event = reviewEventStoreMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("project_id", 9001L).eq("object_id", "501"));
        Long taskCount = reviewTaskStoreMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertEquals(null, event);
        assertEquals(Long.valueOf(0L), taskCount);
    }

    @Test
    void shouldIgnoreWebhookWhenProjectIsInactive() throws Exception {
        insertProject(9002L, false, true);

        invokeMergeRequestWebhook(9002L);

        CodeReviewEventEntity event = reviewEventStoreMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "501"));
        Long taskCount = reviewTaskStoreMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertEquals("IGNORED", event.getStatus());
        assertEquals(Long.valueOf(0L), taskCount);
    }

    @Test
    void shouldIgnoreWebhookWhenAiReviewIsDisabled() throws Exception {
        insertProject(9003L, true, false);

        invokeMergeRequestWebhook(9003L);

        CodeReviewEventEntity event = reviewEventStoreMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "501"));
        Long taskCount = reviewTaskStoreMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertEquals("IGNORED", event.getStatus());
        assertEquals(Long.valueOf(0L), taskCount);
    }

    @Test
    void shouldCreateTaskWhenProjectAllowsAiReview() throws Exception {
        insertProject(9004L, true, true);

        invokeMergeRequestWebhook(9004L)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("accepted"));

        CodeReviewEventEntity event = reviewEventStoreMapper.selectOne(
            new QueryWrapper<CodeReviewEventEntity>().eq("object_id", "501"));
        CodeReviewTaskEntity task = reviewTaskStoreMapper.selectOne(
            new QueryWrapper<CodeReviewTaskEntity>().eq("target_id", "7"));

        assertEquals("TASK_CREATED", event.getStatus());
        assertEquals("MR_REVIEW", task.getTaskType());
        assertEquals(event.getProjectId(), task.getProjectId());
    }

    private ResultActions invokeMergeRequestWebhook(Long projectId) throws Exception {
        return invokeMergeRequestWebhook(projectId, status().isOk());
    }

    private ResultActions invokeMergeRequestWebhook(Long projectId,
                                                    org.springframework.test.web.servlet.ResultMatcher statusMatcher) throws Exception {
        String payload = "{"
            + "\"object_kind\":\"merge_request\","
            + "\"event_type\":\"merge_request\","
            + "\"user\":{\"id\":12,\"name\":\"alice\"},"
            + "\"project\":{\"id\":" + projectId + ",\"name\":\"code-reviewer\"},"
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

        return mockMvc.perform(post("/api/webhooks/gitlab")
            .header("X-Gitlab-Token", "test-gitlab-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(statusMatcher);
    }

    private void insertProject(Long gitlabProjectId, boolean active, boolean aiReviewEnabled) {
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("project:" + gitlabProjectId);
        entity.setProjectName("Project " + gitlabProjectId);
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(gitlabProjectId);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/project-" + gitlabProjectId);
        entity.setGitlabWebhookToken("test-gitlab-token");
        entity.setAiReviewEnabled(aiReviewEnabled);
        entity.setGitlabNoteEnabled(true);
        entity.setWecomNotifyEnabled(false);
        entity.setActive(active);
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        projectProfileMapper.insert(entity);
    }
}
