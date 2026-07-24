package com.vemo.codereview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.auth.client.SsoAuthClient;
import com.vemo.codereview.auth.model.SsoEmployeeProfile;
import com.vemo.codereview.common.config.SsoProperties;
import com.vemo.codereview.common.exception.DomainException;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SsoAuthClientTest {

    private MockWebServer mockWebServer;
    private SsoAuthClient ssoAuthClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        SsoProperties properties = new SsoProperties();
        properties.setBaseUrl(mockWebServer.url("").toString());
        properties.setLoginPath("/sso/login");
        properties.setEmployeeInfoPath("/sso/profile");

        ssoAuthClient = new SsoAuthClient(
            properties,
            new ObjectMapper(),
            new OkHttpClient()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldLoginAndFetchEmployeeProfile() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"code\":\"SUCCESS\",\"data\":{\"token_type\":\"Bearer\",\"access_token\":\"AT-1\",\"expires_in\":7200,\"refresh_token\":\"RT-1\"},\"msg\":null}"));
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"code\":\"SUCCESS\",\"data\":{\"id\":10001,\"code\":\"E0000001\",\"name\":\"Alice Chen\",\"email\":\"yisheng_chen@example.com\"},\"msg\":null}"));

        SsoEmployeeProfile profile = ssoAuthClient.loginAndFetchProfile("E0000001", "plain-password");

        RecordedRequest loginRequest = mockWebServer.takeRequest();
        RecordedRequest profileRequest = mockWebServer.takeRequest();
        assertEquals("/sso/login", loginRequest.getPath());
        assertEquals(null, loginRequest.getHeader("encryption-type"));
        assertEquals("{\"username\":\"E0000001\",\"password\":\"plain-password\"}", loginRequest.getBody().readUtf8());
        assertEquals("/sso/profile", profileRequest.getPath());
        assertEquals("Bearer AT-1", profileRequest.getHeader("Authorization"));
        assertEquals(Long.valueOf(10001L), profile.getSsoUserId());
        assertEquals("E0000001", profile.getEmployeeCode());
        assertEquals("Alice Chen", profile.getName());
        assertEquals("yisheng_chen@example.com", profile.getEmail());
    }

    @Test
    void shouldMapSsoLoginFailureMessage() {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"code\":\"CP00002006\",\"data\":null,\"msg\":\"用户名或密码错误，请重新输入\"}"));

        DomainException ex = assertThrows(DomainException.class,
            () -> ssoAuthClient.loginAndFetchProfile("E0000001", "bad-password"));

        assertEquals("SSO_LOGIN_FAILED", ex.getCode());
        assertEquals("用户名或密码错误，请重新输入", ex.getMessage());
    }
}
