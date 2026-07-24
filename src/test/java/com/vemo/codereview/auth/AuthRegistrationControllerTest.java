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
import com.vemo.codereview.auth.client.SsoAuthClient;
import com.vemo.codereview.auth.model.SsoEmployeeProfile;
import com.vemo.codereview.auth.service.AuthTokenService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @Autowired
    private AuthTokenService authTokenService;

    @MockBean
    private SsoAuthClient ssoAuthClient;

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
                    + "\"email\":\" Alice.Chen@example.com \","
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
        assertEquals("alice.chen@example.com", user.getEmail());
        assertEquals("alice.gitlab", user.getGitlabUsername());
        assertEquals("LOCAL", user.getAuthSource());
        assertTrue(user.getPasswordInitialized());
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
                    + "\"email\":\"bob2@example.com\","
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
                    + "\"email\":\"race@example.com\","
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
                    + "\"email\":\"mallory@example.com\","
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
                    + "\"email\":\"carol@example.com\","
                    + "\"password\":\"123\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_INVALID"));
    }

    @Test
    void shouldRejectRegistrationWithoutEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"noemail\","
                    + "\"displayName\":\"No Email\","
                    + "\"password\":\"123456\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_EMAIL_REQUIRED"))
            .andExpect(jsonPath("$.message").value("邮箱不能为空"));
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        UserEntity user = insertUser("email-owner");
        user.setEmail("owner@example.com");
        userMapper.updateById(user);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"email-other\","
                    + "\"displayName\":\"Email Other\","
                    + "\"email\":\"owner@example.com\","
                    + "\"password\":\"123456\""
                    + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_EMAIL_DUPLICATE"))
            .andExpect(jsonPath("$.message").value("邮箱已存在"));
    }

    @Test
    void shouldSsoLoginAndLinkExistingUserByEmail() throws Exception {
        UserEntity user = insertUser("existing");
        user.setEmail("existing@example.com");
        userMapper.updateById(user);

        SsoEmployeeProfile profile = new SsoEmployeeProfile();
        profile.setSsoUserId(10001L);
        profile.setEmployeeCode("E0000001");
        profile.setName("Alice Chen");
        profile.setEmail("Existing@example.com");
        Mockito.when(ssoAuthClient.loginAndFetchProfile("E0000001", "company-password"))
            .thenReturn(profile);

        mockMvc.perform(post("/api/auth/sso-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"employeeCode\":\"E0000001\","
                    + "\"password\":\"company-password\""
                    + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.username").value("existing"))
            .andExpect(jsonPath("$.data.displayName").value("existing"));

        UserEntity updated = userMapper.selectById(user.getId());
        assertEquals("existing@example.com", updated.getEmail());
        assertEquals("E0000001", updated.getEmployeeCode());
        assertEquals(Long.valueOf(10001L), updated.getSsoUserId());
        assertEquals("LOCAL_SSO", updated.getAuthSource());
        assertTrue(updated.getPasswordInitialized());
    }

    @Test
    void shouldSsoLoginAndAutoCreateUserWhenEmailDoesNotMatch() throws Exception {
        SsoEmployeeProfile profile = new SsoEmployeeProfile();
        profile.setSsoUserId(10002L);
        profile.setEmployeeCode("E0000002");
        profile.setName("张三");
        profile.setEmail("san_zhang1@example.com");
        Mockito.when(ssoAuthClient.loginAndFetchProfile("E0000002", "company-password"))
            .thenReturn(profile);

        mockMvc.perform(post("/api/auth/sso-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"employeeCode\":\"E0000002\","
                    + "\"password\":\"company-password\""
                    + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("zhangsan1"))
            .andExpect(jsonPath("$.data.displayName").value("张三"))
            .andExpect(jsonPath("$.data.role").value("USER"));

        UserEntity created = selectUserByUsername("zhangsan1");
        assertNotNull(created);
        assertEquals("san_zhang1@example.com", created.getEmail());
        assertEquals("E0000002", created.getEmployeeCode());
        assertEquals(Long.valueOf(10002L), created.getSsoUserId());
        assertEquals("SSO", created.getAuthSource());
        assertEquals(Boolean.FALSE, created.getPasswordInitialized());
        assertEquals("USER", created.getRole());
        assertEquals("ENABLE", created.getStatus());
    }

    @Test
    void shouldRejectLocalLoginWhenPasswordIsNotInitialized() throws Exception {
        UserEntity user = insertUser("ssoonly");
        user.setEmail("ssoonly@example.com");
        user.setPasswordInitialized(false);
        userMapper.updateById(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"ssoonly@example.com\","
                    + "\"password\":\"123456\""
                    + "}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_NOT_INITIALIZED"))
            .andExpect(jsonPath("$.message").value("当前账号尚未设置平台密码，请先通过公司账号登录后在个人信息中设置平台密码"));
    }

    @Test
    void shouldSetPlatformPasswordWithoutCurrentPasswordWhenPasswordIsNotInitialized() throws Exception {
        UserEntity user = insertUser("sso-password");
        user.setEmail("sso-password@example.com");
        user.setPasswordInitialized(false);
        userMapper.updateById(user);
        String token = authTokenService.createToken(user);

        mockMvc.perform(post("/api/auth/set-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"654321\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        UserEntity updated = userMapper.selectById(user.getId());
        assertTrue(updated.getPasswordInitialized());
        assertTrue(passwordHashService.matches("654321", updated.getPasswordHash()));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"username\":\"sso-password@example.com\","
                    + "\"password\":\"654321\""
                    + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("sso-password"));
    }

    @Test
    void shouldExposeProfileAndAllowOnlyGitlabUsernameSelfUpdate() throws Exception {
        UserEntity user = insertUser("profile-user");
        user.setEmail("profile-user@example.com");
        user.setEmployeeCode("E0000001");
        user.setSsoUserId(10001L);
        user.setAuthSource("SSO");
        user.setPasswordInitialized(false);
        userMapper.updateById(user);
        String token = authTokenService.createToken(user);

        mockMvc.perform(get("/api/auth/profile")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("profile-user"))
            .andExpect(jsonPath("$.data.email").value("profile-user@example.com"))
            .andExpect(jsonPath("$.data.employeeCode").value("E0000001"))
            .andExpect(jsonPath("$.data.ssoUserId").value(10001))
            .andExpect(jsonPath("$.data.authSource").value("SSO"))
            .andExpect(jsonPath("$.data.passwordInitialized").value(false));

        mockMvc.perform(post("/api/auth/profile/gitlab-username")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gitlabUsername\":\" updated.gitlab \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.gitlabUsername").value("updated.gitlab"));

        UserEntity updated = userMapper.selectById(user.getId());
        assertEquals("profile-user", updated.getUsername());
        assertEquals("profile-user@example.com", updated.getEmail());
        assertEquals("E0000001", updated.getEmployeeCode());
        assertEquals("updated.gitlab", updated.getGitlabUsername());
    }

    private UserEntity selectUserByUsername(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("username", username).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private UserEntity insertUser(String username) {
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
        return user;
    }
}
