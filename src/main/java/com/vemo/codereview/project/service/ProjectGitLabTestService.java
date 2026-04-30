package com.vemo.codereview.project.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.vemo.codereview.project.model.GitLabProjectTestRequest;
import com.vemo.codereview.project.model.GitLabProjectTestResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectGitLabTestService {

    private final GitLabProjectResolver gitLabProjectResolver;

    public ProjectGitLabTestService(GitLabProjectResolver gitLabProjectResolver) {
        this.gitLabProjectResolver = gitLabProjectResolver;
    }

    public GitLabProjectTestResponse test(GitLabProjectTestRequest request) {
        if (request == null || !StringUtils.hasText(request.getGitlabProjectUrl())) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required");
        }
        if (!StringUtils.hasText(request.getGitlabWebhookToken())) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required");
        }
        GitLabProjectPayload payload = gitLabProjectResolver.resolveProject(
            request.getGitlabProjectUrl(),
            request.getGitlabWebhookToken()
        );
        GitLabProjectTestResponse response = new GitLabProjectTestResponse();
        response.setGitlabProjectId(payload.getId());
        response.setProjectName(payload.getName());
        response.setPathWithNamespace(payload.getPathWithNamespace());
        response.setWebUrl(payload.getWebUrl());
        return response;
    }
}
