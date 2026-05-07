package com.vemo.codereview.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.auth.service.AuthTokenService;
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
    private UserMapper userMapper;

    @Autowired
    private AuthTokenService authTokenService;

    private String adminToken;

    @BeforeEach
    void setUpData() throws Exception {
        codeReviewResultMapper.delete(null);
        codeReviewTaskMapper.delete(null);
        codeReviewEventMapper.delete(null);
        userMapper.delete(null);

        adminToken = "Bearer " + authTokenService.createToken(createUser(1L, "admin", "ADMIN"));

        Date now = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2026-04-03");

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("501");
        event.setObjectType("merge_request");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setIdempotentKey("dashboard-1001-501");
        event.setPayloadJson("{}");
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
            .andExpect(jsonPath("$.data.records[0].operatorName").value("alice"));
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
