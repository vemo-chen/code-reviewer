package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CodeReviewerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:webhook-idempotency-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class GitLabWebhookIdempotencyTest {

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
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("project:1001");
        entity.setProjectName("code-reviewer");
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(1001L);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/code-reviewer");
        entity.setGitlabWebhookToken("test-gitlab-token");
        entity.setAiReviewEnabled(true);
        entity.setGitlabNoteEnabled(true);
        entity.setWecomNotifyEnabled(false);
        entity.setActive(true);
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        projectProfileMapper.insert(entity);
    }

    @Test
    void shouldIgnoreDuplicateWebhookPayload() throws Exception {
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
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        Long eventCount = codeReviewEventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>());
        Long taskCount = codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>());

        assertEquals(Long.valueOf(1L), eventCount);
        assertEquals(Long.valueOf(1L), taskCount);
    }
}
