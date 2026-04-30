package com.vemo.codereview.project.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.platform.gitlab.model.GitLabBranchPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GitLabProjectResolver {

    private final GitLabApiClient gitLabApiClient;

    public GitLabProjectResolver(GitLabApiClient gitLabApiClient) {
        this.gitLabApiClient = gitLabApiClient;
    }

    public GitLabProjectPayload resolveProject(String gitlabProjectUrl, String token) {
        String projectPath = extractProjectPath(gitlabProjectUrl);
        return gitLabApiClient.getProjectByPath(extractGitLabBaseUrl(gitlabProjectUrl), projectPath, token);
    }

    public List<GitLabBranchPayload> listBranches(String gitlabProjectUrl, String token) {
        String projectPath = extractProjectPath(gitlabProjectUrl);
        return gitLabApiClient.listProjectBranches(extractGitLabBaseUrl(gitlabProjectUrl), projectPath, token);
    }

    public String extractProjectPath(String gitlabProjectUrl) {
        URI uri = parseGitLabProjectUri(gitlabProjectUrl);
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new DomainException("PROJECT_URL_INVALID", "GitLab project URL is invalid");
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!StringUtils.hasText(normalized)) {
            throw new DomainException("PROJECT_URL_INVALID", "GitLab project URL is invalid");
        }
        return normalized;
    }

    public String extractGitLabBaseUrl(String gitlabProjectUrl) {
        URI uri = parseGitLabProjectUri(gitlabProjectUrl);
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() >= 0) {
            builder.append(':').append(uri.getPort());
        }
        return builder.toString();
    }

    private URI parseGitLabProjectUri(String gitlabProjectUrl) {
        if (!StringUtils.hasText(gitlabProjectUrl)) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required");
        }
        try {
            URI uri = URI.create(gitlabProjectUrl.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new DomainException("PROJECT_URL_INVALID", "GitLab project URL is invalid");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new DomainException("PROJECT_URL_INVALID", "GitLab project URL is invalid");
        }
    }
}
