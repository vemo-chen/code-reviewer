package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    "spring.datasource.url=jdbc:h2:mem:mr-lifecycle-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class GitLabMergeRequestLifecycleTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReviewEventStoreMapper eventMapper;
    @Autowired
    private ReviewTaskStoreMapper taskMapper;
    @Autowired
    private ProjectProfileMapper projectMapper;

    @BeforeEach
    void setUp() {
        taskMapper.delete(new QueryWrapper<CodeReviewTaskEntity>());
        eventMapper.delete(new QueryWrapper<CodeReviewEventEntity>());
        projectMapper.delete(new QueryWrapper<ProjectProfileEntity>());
        ProjectProfileEntity project = new ProjectProfileEntity();
        project.setProjectKey("project:1001");
        project.setProjectName("code-reviewer");
        project.setSourcePlatform("gitlab");
        project.setGitlabProjectId(1001L);
        project.setGitlabProjectUrl("http://gitlab.example.com/group/code-reviewer");
        project.setGitlabWebhookToken("test-gitlab-token");
        project.setReviewBranches("test-cr");
        project.setAiReviewEnabled(true);
        project.setGitlabNoteEnabled(true);
        project.setWecomNotifyEnabled(false);
        project.setActive(true);
        project.setCreatedAt(new Date());
        project.setUpdatedAt(new Date());
        projectMapper.insert(project);
    }

    @Test
    void shouldReuseEventAndTaskAcrossMergeRequestLifecycle() throws Exception {
        deliver("open", "opened", "c1", null, "creator", "Creator");
        CodeReviewTaskEntity runningTask = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>());
        runningTask.setStatus("RUNNING");
        runningTask.setExecutionToken("running-token");
        taskMapper.updateById(runningTask);

        deliver("update", "opened", "c2", null, "editor", "Editor");
        deliver("merge", "merged", "c2", "merge-sha", "merger", "Merger");

        assertEquals(Long.valueOf(1L), eventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>()));
        assertEquals(Long.valueOf(1L), taskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>()));
        CodeReviewEventEntity event = eventMapper.selectOne(new QueryWrapper<CodeReviewEventEntity>());
        CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>());
        assertNotNull(event);
        assertNotNull(task);
        assertEquals("creator", event.getOperatorId());
        assertEquals("Creator", event.getOperatorName());
        assertEquals("test-cr", event.getSubmitBranch());
        assertEquals("MERGED", event.getMrState());
        assertEquals("c2", event.getMrHeadSha());
        assertEquals("merge-sha", event.getMergedSha());
        assertEquals("PENDING", task.getStatus());
        assertNull(task.getExecutionToken());
    }

    @Test
    void shouldSyncDisplayFieldsWithoutResettingTaskWhenMergeRequestCodeIsUnchanged() throws Exception {
        deliver("open", "opened", "c1", null, "creator", "Creator", "Original title");
        CodeReviewTaskEntity runningTask = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>());
        runningTask.setStatus("SUCCESS");
        runningTask.setExecutionToken("finished-token");
        taskMapper.updateById(runningTask);

        deliver("update", "opened", "c1", null, "editor", "Editor", "Updated title");

        assertEquals(Long.valueOf(1L), eventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>()));
        assertEquals(Long.valueOf(1L), taskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>()));
        CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>());
        assertNotNull(task);
        assertEquals("Updated title", task.getTargetTitle());
        assertEquals("SUCCESS", task.getStatus());
        assertEquals("finished-token", task.getExecutionToken());
    }

    private void deliver(String action, String state, String head, String mergedSha,
                         String username, String name) throws Exception {
        deliver(action, state, head, mergedSha, username, name, "Review lifecycle");
    }

    private void deliver(String action, String state, String head, String mergedSha,
                         String username, String name, String title) throws Exception {
        String mergeField = mergedSha == null ? "" : ",\"merge_commit_sha\":\"" + mergedSha + "\"";
        String payload = "{"
            + "\"object_kind\":\"merge_request\","
            + "\"event_type\":\"merge_request\","
            + "\"user\":{\"id\":12,\"username\":\"" + username + "\",\"name\":\"" + name + "\"},"
            + "\"project\":{\"id\":1001,\"name\":\"code-reviewer\"},"
            + "\"object_attributes\":{"
            + "\"id\":501,\"iid\":7,\"title\":\"" + title + "\","
            + "\"action\":\"" + action + "\",\"state\":\"" + state + "\","
            + "\"source_branch\":\"source-cr\",\"target_branch\":\"test-cr\","
            + "\"last_commit\":{\"id\":\"" + head + "\"}" + mergeField
            + "}}";
        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }
}
