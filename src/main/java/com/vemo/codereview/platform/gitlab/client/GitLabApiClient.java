package com.vemo.codereview.platform.gitlab.client;

import com.vemo.codereview.common.config.GitLabProperties;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.model.GitLabBranchPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class GitLabApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final GitLabProperties gitLabProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient;

    @Autowired
    public GitLabApiClient(GitLabProperties gitLabProperties, ObjectMapper objectMapper) {
        this(gitLabProperties, objectMapper, buildClient(gitLabProperties));
    }

    public GitLabApiClient(GitLabProperties gitLabProperties, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
        this.gitLabProperties = gitLabProperties;
        this.objectMapper = objectMapper;
        this.okHttpClient = okHttpClient;
    }

    public GitLabProjectPayload getProjectByPath(String baseUrl, String projectPath, String token) {
        Request request = new Request.Builder()
            .url(resolveBaseUrl(baseUrl) + "/api/v4/projects/" + encodeProjectPath(projectPath))
            .header("PRIVATE-TOKEN", resolveApiToken(token))
            .get()
            .build();
        return execute(request, GitLabProjectPayload.class);
    }

    public List<GitLabBranchPayload> listProjectBranches(String baseUrl, String projectPath, String token) {
        Request request = new Request.Builder()
            .url(resolveBaseUrl(baseUrl) + "/api/v4/projects/" + encodeProjectPath(projectPath)
                + "/repository/branches?per_page=100")
            .header("PRIVATE-TOKEN", resolveApiToken(token))
            .get()
            .build();
        GitLabBranchPayload[] response = execute(request, GitLabBranchPayload[].class);
        return Arrays.asList(response);
    }

    public GitLabChangesPayload getMergeRequestChanges(String baseUrl, Long projectId, String mergeRequestIid, String token) {
        String url = resolveBaseUrl(baseUrl)
            + "/api/v4/projects/" + projectId
            + "/merge_requests/" + mergeRequestIid + "/changes";
        Request request = new Request.Builder()
            .url(url)
            .header("PRIVATE-TOKEN", resolveApiToken(token))
            .get()
            .build();
        return execute(request, GitLabChangesPayload.class);
    }

    public List<GitLabChangesPayload.Change> getCommitDiff(String baseUrl, Long projectId, String commitSha, String token) {
        Request request = new Request.Builder()
            .url(resolveBaseUrl(baseUrl)
                + "/api/v4/projects/" + projectId
                + "/repository/commits/" + commitSha + "/diff")
            .header("PRIVATE-TOKEN", resolveApiToken(token))
            .get()
            .build();
        GitLabChangesPayload.Change[] response = execute(request, GitLabChangesPayload.Change[].class);
        return Arrays.asList(response);
    }

    public void createMergeRequestNote(String baseUrl, Long projectId, String mergeRequestIid,
                                       GitLabNoteRequest noteRequest, String token) {
        try {
            Request request = new Request.Builder()
                .url(resolveBaseUrl(baseUrl)
                    + "/api/v4/projects/" + projectId
                    + "/merge_requests/" + mergeRequestIid + "/notes")
                .header("PRIVATE-TOKEN", resolveApiToken(token))
                .post(RequestBody.create(objectMapper.writeValueAsString(noteRequest), JSON))
                .build();
            executeWithoutResponse(request);
        } catch (IOException ex) {
            throw new DomainException("GITLAB_REQUEST_BUILD_ERROR", "Failed to create GitLab note request");
        }
    }

    public void createCommitNote(String baseUrl, Long projectId, String commitSha,
                                 GitLabCommitNoteRequest noteRequest, String token) {
        try {
            Request request = new Request.Builder()
                .url(resolveBaseUrl(baseUrl)
                    + "/api/v4/projects/" + projectId
                    + "/repository/commits/" + commitSha + "/comments")
                .header("PRIVATE-TOKEN", resolveApiToken(token))
                .post(RequestBody.create(objectMapper.writeValueAsString(noteRequest), JSON))
                .build();
            executeWithoutResponse(request);
        } catch (IOException ex) {
            throw new DomainException("GITLAB_REQUEST_BUILD_ERROR", "Failed to create GitLab commit note request");
        }
    }

    private <T> T execute(Request request, Class<T> responseType) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DomainException(
                    "GITLAB_API_ERROR",
                    "GitLab API request failed. url=" + request.url()
                        + ", status=" + response.code()
                        + ", body=" + (response.body() == null ? "" : response.body().string())
                );
            }
            if (response.body() == null) {
                throw new DomainException("GITLAB_EMPTY_RESPONSE", "GitLab API response body is empty");
            }
            String body = response.body().string();
            try {
                return objectMapper.readValue(body, responseType);
            } catch (IOException ex) {
                throw new DomainException(
                    "GITLAB_RESPONSE_PARSE_ERROR",
                    "Failed to parse GitLab API response. url=" + request.url()
                        + ", status=" + response.code()
                        + ", body=" + abbreviate(body)
                );
            }
        } catch (IOException ex) {
            log.info("gitlab test error.", ex);
            throw new DomainException("GITLAB_API_ERROR", "Failed to call GitLab API");
        }
    }

    private void executeWithoutResponse(Request request) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DomainException(
                    "GITLAB_API_ERROR",
                    "GitLab API request failed. url=" + request.url() + ", status=" + response.code()
                );
            }
        } catch (IOException ex) {
            log.info("gitlab test error", ex);
            throw new DomainException("GITLAB_API_ERROR", "Failed to call GitLab API");
        }
    }

    private String resolveBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new DomainException("GITLAB_BASE_URL_MISSING", "GitLab base URL is required");
        }
        return normalizeBaseUrl(baseUrl);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new DomainException("GITLAB_BASE_URL_MISSING", "GitLab base URL is missing");
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String encodeProjectPath(String projectPath) {
        try {
            return URLEncoder.encode(projectPath, "UTF-8");
        } catch (Exception ex) {
            throw new DomainException("PROJECT_PATH_ENCODE_ERROR", "Failed to encode GitLab project path");
        }
    }

    private String resolveApiToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required");
        }
        return token.trim();
    }

    private static OkHttpClient buildClient(GitLabProperties gitLabProperties) {
        int connectTimeoutMs = gitLabProperties.getConnectTimeoutMs() <= 0 ? 5000 : gitLabProperties.getConnectTimeoutMs();
        int readTimeoutMs = gitLabProperties.getReadTimeoutMs() <= 0 ? 15000 : gitLabProperties.getReadTimeoutMs();
        return new OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 500) {
            return body;
        }
        return body.substring(0, 500) + "...";
    }
}
