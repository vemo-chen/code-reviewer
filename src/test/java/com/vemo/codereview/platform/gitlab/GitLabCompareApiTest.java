package com.vemo.codereview.platform.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.common.config.GitLabProperties;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.platform.gitlab.model.GitLabComparePayload;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitLabCompareApiTest {

    private MockWebServer server;
    private GitLabApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        GitLabProperties properties = new GitLabProperties();
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(3000);
        client = new GitLabApiClient(properties, new ObjectMapper(), new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCompareStraightRangeAndMapDiffs() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"compare_timeout\":false,\"compare_same_ref\":false,"
            + "\"diffs\":[{\"old_path\":\"src/A.java\",\"new_path\":\"src/A.java\","
            + "\"diff\":\"@@ -1 +1 @@\",\"collapsed\":false,\"too_large\":false}]}"));

        GitLabComparePayload result = client.compare(server.url("").toString(), 1001L,
            "base-a", "head-c", "token");

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/v4/projects/1001/repository/compare?from=base-a&to=head-c&straight=true",
            request.getPath());
        assertEquals(1, result.getDiffs().size());
        assertEquals("src/A.java", result.getDiffs().get(0).getNewPath());
    }

    @Test
    void shouldRejectIncompleteCompare() {
        server.enqueue(new MockResponse().setBody("{\"compare_timeout\":true,\"diffs\":[]}"));
        DomainException exception = assertThrows(DomainException.class,
            () -> client.compare(server.url("").toString(), 1001L, "base", "head", "token"));
        assertEquals("GITLAB_COMPARE_INCOMPLETE", exception.getCode());
    }
}
