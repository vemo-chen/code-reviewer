package com.vemo.codereview.platform.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.common.config.GitLabProperties;
import com.vemo.codereview.platform.gitlab.model.GitLabBranchPayload;
import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitLabApiClientTest {

    private MockWebServer mockWebServer;
    private GitLabApiClient gitLabApiClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        GitLabProperties gitLabProperties = new GitLabProperties();
        gitLabProperties.setConnectTimeoutMs(3000);
        gitLabProperties.setReadTimeoutMs(3000);

        gitLabApiClient = new GitLabApiClient(gitLabProperties, new ObjectMapper(), new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchProjectByPath() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":1001,\"name\":\"mas-core\",\"path_with_namespace\":\"group/subgroup/mas-core\",\"web_url\":\"http://gitlab.example.com/group/subgroup/mas-core\"}"));

        GitLabProjectPayload payload = gitLabApiClient.getProjectByPath(
            mockWebServer.url("").toString(),
            "group/subgroup/mas-core",
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/group%2Fsubgroup%2Fmas-core", request.getPath());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(Long.valueOf(1001L), payload.getId());
        assertEquals("group/subgroup/mas-core", payload.getPathWithNamespace());
        assertEquals("http://gitlab.example.com/group/subgroup/mas-core", payload.getWebUrl());
    }

    @Test
    void shouldFetchProjectByPathFromExplicitBaseUrl() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":1002,\"name\":\"mas-core\",\"path_with_namespace\":\"group/subgroup/mas-core\",\"web_url\":\"http://gitlab.example.com/group/subgroup/mas-core\"}"));

        GitLabProjectPayload payload = gitLabApiClient.getProjectByPath(
            mockWebServer.url("").toString(),
            "group/subgroup/mas-core",
            "project-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/group%2Fsubgroup%2Fmas-core", request.getPath());
        assertEquals("project-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(Long.valueOf(1002L), payload.getId());
    }

    @Test
    void shouldListProjectBranches() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"name\":\"main\",\"default\":true,\"protected\":true},{\"name\":\"develop\",\"default\":false,\"protected\":false}]"));

        List<GitLabBranchPayload> branches = gitLabApiClient.listProjectBranches(
            mockWebServer.url("").toString(),
            "group/subgroup/mas-core",
            "project-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/group%2Fsubgroup%2Fmas-core/repository/branches?per_page=100", request.getPath());
        assertEquals("project-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(2, branches.size());
        assertEquals("main", branches.get(0).getName());
        assertEquals(Boolean.TRUE, branches.get(0).getDefaultBranch());
        assertEquals(Boolean.TRUE, branches.get(0).getProtectedBranch());
    }

    @Test
    void shouldFetchMergeRequestChanges() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":501,\"iid\":7,\"title\":\"Add review pipeline\",\"changes\":[{\"old_path\":\"src/A.java\",\"new_path\":\"src/A.java\",\"diff\":\"@@ -1 +1 @@\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]}"));

        GitLabChangesPayload response = gitLabApiClient.getMergeRequestChanges(
            mockWebServer.url("").toString(),
            1001L,
            "7",
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/1001/merge_requests/7/changes", request.getPath());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(Long.valueOf(501L), response.getId());
        assertEquals(1, response.getChanges().size());
        assertEquals("src/A.java", response.getChanges().get(0).getNewPath());
        assertEquals(Boolean.FALSE, response.getChanges().get(0).getNewFile());
    }

    @Test
    void shouldFetchCommitDiff() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("[{\"old_path\":\"src/A.java\",\"new_path\":\"src/A.java\",\"diff\":\"@@ -1 +1 @@\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]"));

        List<GitLabChangesPayload.Change> changes = gitLabApiClient.getCommitDiff(
            mockWebServer.url("").toString(),
            1001L,
            "abcdef123456",
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/1001/repository/commits/abcdef123456/diff", request.getPath());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertEquals(1, changes.size());
        assertEquals("src/A.java", changes.get(0).getNewPath());
        assertEquals(Boolean.FALSE, changes.get(0).getDeletedFile());
    }

    @Test
    void shouldCreateMergeRequestNote() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":1}"));

        gitLabApiClient.createMergeRequestNote(
            mockWebServer.url("").toString(),
            1001L,
            "7",
            new GitLabNoteRequest("review summary"),
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/1001/merge_requests/7/notes", request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertTrue(request.getBody().readUtf8().contains("review summary"));
    }

    @Test
    void shouldCreateCommitNote() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":1}"));

        gitLabApiClient.createCommitNote(
            mockWebServer.url("").toString(),
            1001L,
            "abcdef123456",
            new GitLabCommitNoteRequest("push review summary"),
            "gitlab-access-token"
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/v4/projects/1001/repository/commits/abcdef123456/comments", request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("gitlab-access-token", request.getHeader("PRIVATE-TOKEN"));
        assertTrue(request.getBody().readUtf8().contains("push review summary"));
    }
}
