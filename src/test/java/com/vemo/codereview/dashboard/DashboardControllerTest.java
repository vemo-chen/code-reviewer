package com.vemo.codereview.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.auth.service.AuthTokenService;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CodeReviewerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:dashboard-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class DashboardControllerTest {

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

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthTokenService authTokenService;

    private String adminToken;
    private Long reviewTaskId;

    @BeforeEach
    void setUpData() throws Exception {
        codeReviewResultMapper.delete(null);
        codeReviewTaskMapper.delete(null);
        codeReviewEventMapper.delete(null);
        projectProfileMapper.delete(null);
        userMapper.delete(null);

        adminToken = "Bearer " + authTokenService.createToken(createUser(1L, "admin", "ADMIN"));

        Date now = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2026-04-03");
        Date submitTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-04-02 09:30:00");

        ProjectProfileEntity project = new ProjectProfileEntity();
        project.setId(1001L);
        project.setProjectKey("project:test");
        project.setProjectName("code-reviewer");
        project.setSourcePlatform("gitlab");
        project.setGitlabProjectId(2001L);
        project.setOwnerUserId(1L);
        project.setAiReviewEnabled(true);
        project.setReviewContextEnabled(true);
        project.setGitlabNoteEnabled(true);
        project.setWecomNotifyEnabled(false);
        project.setActive(true);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectProfileMapper.insert(project);

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("501");
        event.setObjectType("merge_request");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setSubmitTime(submitTime);
        event.setIdempotentKey("dashboard-1001-501");
        event.setPayloadJson("{\"object_attributes\":{\"created_at\":\"2026-04-02T09:30:00+08:00\"}}");
        event.setStatus("TASK_CREATED");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        codeReviewEventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("7");
        task.setTargetTitle("Add dashboard apis");
        task.setStatus("SUCCESS");
        task.setRetryCount(1);
        task.setCreatedAt(now);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);
        reviewTaskId = task.getId();

        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setTaskId(task.getId());
        result.setProviderName("openai-compatible");
        result.setModelName("gpt-4o-mini");
        result.setRiskLevel("HIGH");
        result.setSuggestedScore(88);
        result.setDeductionScore(12);
        result.setFinalScore(76);
        result.setScoreReason("Deducted 12 points for HIGH issues");
        result.setSummary("Found one important issue");
        result.setBriefSummary("Found one important issue");
        result.setAdvice("Fix before merge");
        result.setCreatedAt(now);
        codeReviewResultMapper.insert(result);
    }

    @Test
    void shouldReturnReviewTaskPage() throws Exception {
        mockMvc.perform(get("/api/dashboard/review-tasks")
                .header("Authorization", adminToken)
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].projectName").value("code-reviewer"))
            .andExpect(jsonPath("$.data.records[0].riskLevel").value("HIGH"))
            .andExpect(jsonPath("$.data.records[0].finalScore").value(76))
            .andExpect(jsonPath("$.data.records[0].operatorName").value("alice"))
            .andExpect(jsonPath("$.data.records[0].submitTime").value("2026-04-02T09:30:00.000+08:00"));
    }

    @Test
    void shouldOrderReviewTaskPageBySubmitTimeDesc() throws Exception {
        Date laterCreatedAt = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2026-04-04");
        Date earlierSubmitTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-04-01 08:00:00");

        CodeReviewEventEntity secondEvent = new CodeReviewEventEntity();
        secondEvent.setSourcePlatform("gitlab");
        secondEvent.setEventType("push");
        secondEvent.setProjectId(1001L);
        secondEvent.setProjectName("code-reviewer");
        secondEvent.setObjectId("sha-002");
        secondEvent.setObjectType("commit");
        secondEvent.setOperatorId("u002");
        secondEvent.setOperatorName("bob");
        secondEvent.setSubmitTime(earlierSubmitTime);
        secondEvent.setIdempotentKey("dashboard-1001-sha-002");
        secondEvent.setPayloadJson("{\"commits\":[{\"timestamp\":\"2026-04-01T08:00:00+08:00\"}]}");
        secondEvent.setStatus("TASK_CREATED");
        secondEvent.setCreatedAt(laterCreatedAt);
        secondEvent.setUpdatedAt(laterCreatedAt);
        codeReviewEventMapper.insert(secondEvent);

        CodeReviewTaskEntity secondTask = new CodeReviewTaskEntity();
        secondTask.setEventId(secondEvent.getId());
        secondTask.setTaskType("PUSH_REVIEW");
        secondTask.setSourcePlatform("gitlab");
        secondTask.setProjectId(1001L);
        secondTask.setTargetId("sha-002");
        secondTask.setTargetTitle("Older submit newer task");
        secondTask.setStatus("SUCCESS");
        secondTask.setRetryCount(0);
        secondTask.setCreatedAt(laterCreatedAt);
        secondTask.setFinishedAt(laterCreatedAt);
        secondTask.setUpdatedAt(laterCreatedAt);
        codeReviewTaskMapper.insert(secondTask);

        mockMvc.perform(get("/api/dashboard/review-tasks")
                .header("Authorization", adminToken)
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.records[0].targetTitle").value("Add dashboard apis"))
            .andExpect(jsonPath("$.data.records[0].submitTime").value("2026-04-02T09:30:00.000+08:00"))
            .andExpect(jsonPath("$.data.records[1].targetTitle").value("Older submit newer task"))
            .andExpect(jsonPath("$.data.records[1].submitTime").value("2026-04-01T08:00:00.000+08:00"));
    }

    @Test
    void shouldReturnReviewTaskDetailWithGitLabSubmitTime() throws Exception {
        mockMvc.perform(get("/api/dashboard/review-tasks/" + reviewTaskId)
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.targetTitle").value("Add dashboard apis"))
            .andExpect(jsonPath("$.data.submitTime").value("2026-04-02T09:30:00.000+08:00"))
            .andExpect(jsonPath("$.data.createdAt").value("2026-04-03T00:00:00.000+08:00"));
    }

    @Test
    void shouldReturnProjectStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/project-stats")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalProjects").value(1))
            .andExpect(jsonPath("$.data.totalTasks").value(1))
            .andExpect(jsonPath("$.data.highRiskTasks").value(1))
            .andExpect(jsonPath("$.data.projects[0].projectName").value("code-reviewer"))
            .andExpect(jsonPath("$.data.projects[0].averageFinalScore").value(76.0));
    }

    @Test
    void shouldReturnDeveloperStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/developer-stats")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalDevelopers").value(1))
            .andExpect(jsonPath("$.data.totalTasks").value(1))
            .andExpect(jsonPath("$.data.highRiskTasks").value(1))
            .andExpect(jsonPath("$.data.developers[0].developerName").value("alice"))
            .andExpect(jsonPath("$.data.developers[0].averageFinalScore").value(76.0));
    }

    @Test
    void shouldReturnScoreStatsForDateRange() throws Exception {
        mockMvc.perform(get("/api/dashboard/score-stats")
                .header("Authorization", adminToken)
                .param("startDate", "2026-04-01")
                .param("endDate", "2026-04-08"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.reviewCount").value(1))
            .andExpect(jsonPath("$.data.averageFinalScore").value(76.0))
            .andExpect(jsonPath("$.data.projects[0].projectName").value("code-reviewer"))
            .andExpect(jsonPath("$.data.developers[0].developerName").value("alice"));
    }

    private UserEntity createUser(Long id, String username, String role) {
        Date now = new Date();
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setDisplayName(username);
        user.setRole(role);
        user.setStatus("ENABLE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
