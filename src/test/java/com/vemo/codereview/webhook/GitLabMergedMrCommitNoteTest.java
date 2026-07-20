package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.platform.gitlab.service.GitLabCommentPublisher;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
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
    "spring.datasource.url=jdbc:h2:mem:gitlab-merged-mr-note-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class GitLabMergedMrCommitNoteTest {

    @MockBean
    private GitLabCommentPublisher gitLabCommentPublisher;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewEventStoreMapper codeReviewEventMapper;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @Autowired
    private ReviewResultStoreMapper codeReviewResultMapper;

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @BeforeEach
    void setUp() {
        codeReviewResultMapper.delete(new QueryWrapper<CodeReviewResultEntity>());
        codeReviewTaskMapper.delete(new QueryWrapper<CodeReviewTaskEntity>());
        codeReviewEventMapper.delete(new QueryWrapper<CodeReviewEventEntity>());
        projectProfileMapper.delete(new QueryWrapper<ProjectProfileEntity>());
    }

    @Test
    void shouldPublishReviewedMrResultOnceToMergeCommit() throws Exception {
        ProjectProfileEntity project = insertProject(1001L, true);
        CodeReviewEventEntity mrEvent = insertSuccessfulMrEvent(project);
        CodeReviewTaskEntity mrTask = insertSuccessfulMrTask(project, mrEvent);
        CodeReviewResultEntity result = insertResult(mrTask);

        String payload = mergePushPayload(1001L, "test-cr", "c2", "merge-sha");

        deliver(payload);
        deliver(payload);

        assertEquals(Long.valueOf(2L),
            codeReviewEventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>()));
        assertEquals(Long.valueOf(1L),
            codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>()));
        verify(gitLabCommentPublisher, times(1)).publishMergedMrCommit(
            eq("http://gitlab.example.com/group/project-1001"),
            eq(1001L),
            eq("merge-sha"),
            eq("Review lifecycle"),
            eq("test-cr"),
            argThat(savedResult -> savedResult != null && result.getId().equals(savedResult.getId())),
            eq("test-gitlab-token"));
    }

    @Test
    void shouldNotPublishMergeCommitNoteWhenProjectGitLabNoteIsDisabled() throws Exception {
        ProjectProfileEntity project = insertProject(1001L, false);
        CodeReviewEventEntity mrEvent = insertSuccessfulMrEvent(project);
        CodeReviewTaskEntity mrTask = insertSuccessfulMrTask(project, mrEvent);
        insertResult(mrTask);

        deliver(mergePushPayload(1001L, "test-cr", "c2", "merge-sha"));

        assertEquals(Long.valueOf(2L),
            codeReviewEventMapper.selectCount(new QueryWrapper<CodeReviewEventEntity>()));
        assertEquals(Long.valueOf(1L),
            codeReviewTaskMapper.selectCount(new QueryWrapper<CodeReviewTaskEntity>()));
        verify(gitLabCommentPublisher, never()).publishMergedMrCommit(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(CodeReviewResultEntity.class),
            org.mockito.ArgumentMatchers.anyString());
    }

    private void deliver(String payload) throws Exception {
        mockMvc.perform(post("/api/webhooks/gitlab")
                .header("X-Gitlab-Token", "test-gitlab-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }

    private ProjectProfileEntity insertProject(Long gitlabProjectId, boolean gitlabNoteEnabled) {
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("project:" + gitlabProjectId);
        entity.setProjectName("Project " + gitlabProjectId);
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(gitlabProjectId);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/project-" + gitlabProjectId);
        entity.setGitlabWebhookToken("test-gitlab-token");
        entity.setReviewBranches("test-cr");
        entity.setAiReviewEnabled(true);
        entity.setGitlabNoteEnabled(gitlabNoteEnabled);
        entity.setWecomNotifyEnabled(false);
        entity.setActive(true);
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        projectProfileMapper.insert(entity);
        return entity;
    }

    private CodeReviewEventEntity insertSuccessfulMrEvent(ProjectProfileEntity project) {
        Date now = new Date();
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(project.getId());
        event.setProjectName(project.getProjectName());
        event.setObjectId("501");
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("c2");
        event.setMergedSha("merge-sha");
        event.setIdempotentKey("gitlab:mr:1001:501");
        event.setPayloadJson("{}");
        event.setStatus("PROCESSED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        codeReviewEventMapper.insert(event);
        return event;
    }

    private CodeReviewTaskEntity insertSuccessfulMrTask(ProjectProfileEntity project, CodeReviewEventEntity event) {
        Date now = new Date();
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(project.getId());
        task.setTargetId("7");
        task.setTargetTitle("Review lifecycle");
        task.setStatus("SUCCESS");
        task.setFixStatus("TO_BE_FIXED");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);
        return task;
    }

    private CodeReviewResultEntity insertResult(CodeReviewTaskEntity task) {
        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setTaskId(task.getId());
        result.setProviderName("openai-compatible");
        result.setModelName("model");
        result.setRiskLevel("LOW");
        result.setSummary("Review passed");
        result.setCreatedAt(new Date());
        codeReviewResultMapper.insert(result);
        return result;
    }

    private String mergePushPayload(Long gitlabProjectId, String branch, String headSha, String mergeSha) {
        return "{"
            + "\"object_kind\":\"push\","
            + "\"event_type\":\"push\","
            + "\"user_name\":\"alice\","
            + "\"user_username\":\"alice\","
            + "\"ref\":\"refs/heads/" + branch + "\","
            + "\"before\":\"111111111111\","
            + "\"after\":\"" + mergeSha + "\","
            + "\"project\":{\"id\":" + gitlabProjectId + ",\"name\":\"code-reviewer\"},"
            + "\"commits\":["
            + "{\"id\":\"" + headSha + "\",\"message\":\"Feature change\",\"title\":\"Feature change\"},"
            + "{\"id\":\"" + mergeSha + "\",\"message\":\"Merge branch 'source-cr' into '" + branch
            + "'\",\"title\":\"Merge branch 'source-cr' into '" + branch + "'\"}"
            + "]"
            + "}";
    }
}
