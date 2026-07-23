package com.vemo.codereview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.auth.service.PasswordHashService;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import com.vemo.codereview.user.mapper.UserProjectRelMapper;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = CodeReviewerApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:auth-registration-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class AuthRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private UserMapper userMapper;

    @Autowired
    private UserProjectRelMapper userProjectRelMapper;

    @Autowired
    private PasswordHashService passwordHashService;

    @BeforeEach
    void clearData() {
        userProjectRelMapper.delete(null);
        userMapper.delete(null);
    }

    @AfterEach
    void resetSpies() {
        Mockito.reset(userMapper);
    }

    @Test
    void shouldRegisterEnabledMemberAndReturnUsableToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\" alice \","
                    + "\"displayName\":\" Alice Chen \","
                    + "\"password\":\"123456\","
                    + "\"gitlabUsername\":\" alice.gitlab \""
                    + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.username").value("alice"))
            .andExpect(jsonPath("$.data.displayName").value("Alice Chen"))
            .andExpect(jsonPath("$.data.role").value("USER"))
            .andReturn();

        UserEntity user = selectUserByUsername("alice");
        assertNotNull(user);
        assertEquals("Alice Chen", user.getDisplayName());
        assertEquals("alice.gitlab", user.getGitlabUsername());
        assertEquals("USER", user.getRole());
        assertEquals("ENABLE", user.getStatus());
        assertTrue(passwordHashService.matches("123456", user.getPasswordHash()));

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("alice"))
            .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void shouldRejectDuplicateUsername() throws Exception {
        insertUser("bob");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"bob\","
                    + "\"displayName\":\"Bob\","
                    + "\"password\":\"123456\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_USERNAME_DUPLICATE"))
            .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void shouldMapDuplicateKeyToDuplicateUsername() throws Exception {
        Mockito.doThrow(new DuplicateKeyException("duplicate username"))
            .when(userMapper).insert(Mockito.any(UserEntity.class));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"race\","
                    + "\"displayName\":\"Race\","
                    + "\"password\":\"123456\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_USERNAME_DUPLICATE"))
            .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void shouldIgnoreHostileRoleStatusAndProjectFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"mallory\","
                    + "\"displayName\":\"Mallory\","
                    + "\"password\":\"123456\","
                    + "\"gitlabUsername\":\"mallory.gitlab\","
                    + "\"role\":\"ADMIN\","
                    + "\"status\":\"DISABLE\","
                    + "\"projectIds\":[1,2],"
                    + "\"memberUserIds\":[1]"
                    + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("mallory"))
            .andExpect(jsonPath("$.data.role").value("USER"));

        UserEntity user = selectUserByUsername("mallory");
        assertNotNull(user);
        assertEquals("USER", user.getRole());
        assertEquals("ENABLE", user.getStatus());
        assertEquals("mallory.gitlab", user.getGitlabUsername());
        assertEquals(Long.valueOf(0L), userProjectRelMapper.selectCount(null));
    }

    @Test
    void shouldRejectInvalidRegisterRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"carol\","
                    + "\"displayName\":\"Carol\","
                    + "\"password\":\"123\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_INVALID"));
    }

    private UserEntity selectUserByUsername(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("username", username).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private void insertUser(String username) {
        Date now = new Date();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(passwordHashService.sha256("123456"));
        user.setRole("USER");
        user.setStatus("ENABLE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }
}
