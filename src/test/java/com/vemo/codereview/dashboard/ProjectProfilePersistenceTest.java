package com.vemo.codereview.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:project-profile-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class ProjectProfilePersistenceTest {

    @Autowired
    private ProjectProfileMapper projectProfileMapper;

    @Test
    void shouldPersistExpandedProjectProfileFields() {
        Date now = new Date();

        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey("gitlab:1001");
        entity.setProjectName("MAS Core");
        entity.setSourcePlatform("gitlab");
        entity.setGitlabProjectId(1001L);
        entity.setGitlabProjectUrl("http://gitlab.example.com/group/mas-core");
        entity.setAiReviewEnabled(Boolean.TRUE);
        entity.setGitlabNoteEnabled(Boolean.TRUE);
        entity.setWecomNotifyEnabled(Boolean.TRUE);
        entity.setWecomWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test");
        entity.setPromptContent("Review this project using team rules");
        entity.setActive(Boolean.TRUE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        projectProfileMapper.insert(entity);

        ProjectProfileEntity saved = projectProfileMapper.selectById(entity.getId());

        assertNotNull(saved);
        assertEquals(Long.valueOf(1001L), saved.getGitlabProjectId());
        assertEquals("http://gitlab.example.com/group/mas-core", saved.getGitlabProjectUrl());
        assertEquals(Boolean.TRUE, saved.getAiReviewEnabled());
        assertEquals(Boolean.TRUE, saved.getGitlabNoteEnabled());
        assertEquals(Boolean.TRUE, saved.getWecomNotifyEnabled());
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test", saved.getWecomWebhookUrl());
        assertEquals("Review this project using team rules", saved.getPromptContent());
    }
}
