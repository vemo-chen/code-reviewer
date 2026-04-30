package com.vemo.codereview.project.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.model.GitLabBranchPayload;
import com.vemo.codereview.project.model.GitLabBranchOptionResponse;
import com.vemo.codereview.project.model.GitLabProjectTestRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectGitLabBranchService {

    private final GitLabProjectResolver gitLabProjectResolver;

    public ProjectGitLabBranchService(GitLabProjectResolver gitLabProjectResolver) {
        this.gitLabProjectResolver = gitLabProjectResolver;
    }

    public List<GitLabBranchOptionResponse> listBranches(GitLabProjectTestRequest request) {
        if (request == null || !StringUtils.hasText(request.getGitlabProjectUrl())) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required");
        }
        if (!StringUtils.hasText(request.getGitlabWebhookToken())) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required");
        }

        List<GitLabBranchPayload> branches = gitLabProjectResolver.listBranches(
            request.getGitlabProjectUrl(),
            request.getGitlabWebhookToken()
        );
        List<GitLabBranchOptionResponse> responses = new ArrayList<GitLabBranchOptionResponse>();
        for (GitLabBranchPayload branch : branches) {
            if (branch == null || !StringUtils.hasText(branch.getName())) {
                continue;
            }
            GitLabBranchOptionResponse response = new GitLabBranchOptionResponse();
            response.setName(branch.getName());
            response.setDefaultBranch(branch.getDefaultBranch());
            response.setProtectedBranch(branch.getProtectedBranch());
            responses.add(response);
        }
        return responses;
    }
}
