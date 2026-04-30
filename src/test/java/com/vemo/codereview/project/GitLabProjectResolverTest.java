package com.vemo.codereview.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vemo.codereview.common.config.GitLabProperties;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.vemo.codereview.project.service.GitLabProjectResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitLabProjectResolverTest {

    private MockWebServer mockWebServer;
    private GitLabProjectResolver gitLabProjectResolver;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        GitLabProperties properties = new GitLabProperties();
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(3000);

        GitLabApiClient client = new GitLabApiClient(properties, new ObjectMapper(), new OkHttpClient());
        gitLabProjectResolver = new GitLabProjectResolver(client);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldResolveProjectByGitLabUrl() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":888,\"name\":\"mas-core\",\"path_with_namespace\":\"group/subgroup/mas-core\",\"web_url\":\"http://gitlab.example.com/group/subgroup/mas-core\"}"));

        GitLabProjectPayload payload = gitLabProjectResolver.resolveProject(
            mockWebServer.url("/group/subgroup/mas-core.git").toString(),
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/group%2Fsubgroup%2Fmas-core", request.getPath());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(Long.valueOf(888L), payload.getId());
        assertEquals("group/subgroup/mas-core", payload.getPathWithNamespace());
    }

    @Test
    void shouldExtractProjectPathFromGitLabUrl() {
        assertEquals(
            "group/subgroup/mas-core",
            gitLabProjectResolver.extractProjectPath("http://gitlab.example.com/group/subgroup/mas-core.git")
        );
    }

    @Test
    void shouldExtractGitLabBaseUrlFromProjectUrl() {
        assertEquals(
            "http://gitlab.example.com:6060",
            gitLabProjectResolver.extractGitLabBaseUrl("http://gitlab.example.com:6060/group/subgroup/mas-core.git")
        );
    }

    @Test
    void shouldRejectInvalidGitLabUrl() {
        assertThrows(DomainException.class, () -> gitLabProjectResolver.extractProjectPath("not-a-url"));
    }
}
