package com.vemo.codereview.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.auth.service.AuthTokenService;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.vemo.codereview.project.service.GitLabProjectResolver;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.mapper.UserMapper;
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
    "spring.datasource.url=jdbc:h2:mem:project-controller-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthTokenService authTokenService;

    @MockBean
    private GitLabProjectResolver gitLabProjectResolver;

    private String adminToken;

    @BeforeEach
    void clearData() {
        projectProfileMapper.delete(null);
        userMapper.delete(null);
        adminToken = "Bearer " + authTokenService.createToken(createUser(1L, "admin", "ADMIN"));
        userMapper.insert(createUser(2L, "owner", "PROJECT_OWNER"));
        when(gitLabProjectResolver.resolveProject(eq("http://gitlab.example.com/group/mas-core"), eq("project-token")))
            .thenReturn(buildProjectPayload(1001L, "http://gitlab.example.com/group/mas-core"));
        when(gitLabProjectResolver.resolveProject(eq("http://gitlab.example.com/group/mas-core-updated"), eq("project-token")))
            .thenReturn(buildProjectPayload(1002L, "http://gitlab.example.com/group/mas-core-updated"));
    }

    @Test
    void shouldCreateGetUpdateListRefreshAndDeleteProject() throws Exception {
        String createPayload = "{"
            + "\"projectName\":\"MAS Core\","
            + "\"gitlabProjectUrl\":\"http://gitlab.example.com/group/mas-core\","
            + "\"gitlabWebhookToken\":\"project-token\","
            + "\"ownerUserId\":2,"
            + "\"memberUserIds\":[2],"
            + "\"aiReviewEnabled\":false,"
            + "\"gitlabNoteEnabled\":true,"
            + "\"wecomNotifyEnabled\":true,"
            + "\"wecomWebhookUrl\":\"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test\","
            + "\"promptContent\":\"Review this project using team rules\","
            + "\"active\":true"
            + "}";

        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", adminToken)
                .content(createPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.projectName").value("MAS Core"))
            .andExpect(jsonPath("$.data.gitlabProjectId").value(1001))
            .andExpect(jsonPath("$.data.gitlabProjectUrl").value("http://gitlab.example.com/group/mas-core"))
            .andExpect(jsonPath("$.data.aiReviewEnabled").value(false))
            .andExpect(jsonPath("$.data.reviewContextEnabled").value(true))
            .andExpect(jsonPath("$.data.wecomNotifyEnabled").value(true));

        mockMvc.perform(get("/api/projects/1")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gitlabProjectUrl").value("http://gitlab.example.com/group/mas-core"))
            .andExpect(jsonPath("$.data.reviewContextEnabled").value(true))
            .andExpect(jsonPath("$.data.promptContent").value("Review this project using team rules"));

        String updatePayload = "{"
            + "\"projectName\":\"MAS Core Updated\","
            + "\"gitlabProjectUrl\":\"http://gitlab.example.com/group/mas-core-updated\","
            + "\"gitlabWebhookToken\":\"project-token\","
            + "\"ownerUserId\":2,"
            + "\"memberUserIds\":[2],"
            + "\"aiReviewEnabled\":false,"
            + "\"reviewContextEnabled\":false,"
            + "\"gitlabNoteEnabled\":false,"
            + "\"wecomNotifyEnabled\":false,"
            + "\"promptContent\":\"Review DTO boundaries\","
            + "\"active\":true"
            + "}";

        mockMvc.perform(put("/api/projects/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", adminToken)
                .content(updatePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.projectName").value("MAS Core Updated"))
            .andExpect(jsonPath("$.data.gitlabProjectId").value(1002))
            .andExpect(jsonPath("$.data.aiReviewEnabled").value(false))
            .andExpect(jsonPath("$.data.reviewContextEnabled").value(false))
            .andExpect(jsonPath("$.data.gitlabNoteEnabled").value(false))
            .andExpect(jsonPath("$.data.wecomNotifyEnabled").value(false));

        mockMvc.perform(get("/api/projects")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].projectName").value("MAS Core Updated"))
            .andExpect(jsonPath("$.data.records[0].gitlabProjectId").value(1002));

        mockMvc.perform(post("/api/projects/1/refresh")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gitlabProjectId").value(1002));

        mockMvc.perform(delete("/api/projects/1")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("deleted"));

        assertEquals(Long.valueOf(0L), projectProfileMapper.selectCount(null));
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

    private GitLabProjectPayload buildProjectPayload(Long id, String webUrl) {
        GitLabProjectPayload payload = new GitLabProjectPayload();
        payload.setId(id);
        payload.setName("mas-core");
        payload.setPathWithNamespace("group/mas-core");
        payload.setWebUrl(webUrl);
        return payload;
    }
}
