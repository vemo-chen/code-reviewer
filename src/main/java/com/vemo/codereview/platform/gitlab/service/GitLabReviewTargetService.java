package com.vemo.codereview.platform.gitlab.service;

import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabNoteRequest;
import com.vemo.codereview.project.service.GitLabProjectResolver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class GitLabReviewTargetService {

    private final GitLabApiClient gitLabApiClient;
    private final GitLabProjectResolver gitLabProjectResolver;

    public GitLabReviewTargetService(GitLabApiClient gitLabApiClient, GitLabProjectResolver gitLabProjectResolver) {
        this.gitLabApiClient = gitLabApiClient;
        this.gitLabProjectResolver = gitLabProjectResolver;
    }

    public GitLabChangesPayload getMergeRequestChanges(Long projectId, String mergeRequestIid) {
        return getMergeRequestChanges(projectId, mergeRequestIid, null);
    }

    public GitLabChangesPayload getMergeRequestChanges(Long projectId, String mergeRequestIid, String token) {
        return getMergeRequestChanges(null, projectId, mergeRequestIid, token);
    }

    public GitLabChangesPayload getMergeRequestChanges(String gitlabProjectUrl, Long projectId,
                                                       String mergeRequestIid, String token) {
        long startNs = System.nanoTime();
        GitLabChangesPayload payload = gitLabApiClient.getMergeRequestChanges(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            mergeRequestIid,
            token
        );
        log.info("gitlab merge request changes fetched. projectId={}, mergeRequestIid={}, changes={}, elapsedMs={}",
            projectId,
            mergeRequestIid,
            payload == null || payload.getChanges() == null ? 0 : payload.getChanges().size(),
            elapsedMs(startNs));
        return payload;
    }

    public GitLabChangesPayload getCommitChanges(Long projectId, String commitSha, String title) {
        return getCommitChanges(projectId, commitSha, title, null);
    }

    public GitLabChangesPayload getCommitChanges(Long projectId, String commitSha, String title, String token) {
        return getCommitChanges(null, projectId, commitSha, title, token);
    }

    public GitLabChangesPayload getCommitChanges(String gitlabProjectUrl, Long projectId,
                                                 String commitSha, String title, String token) {
        long startNs = System.nanoTime();
        List<GitLabChangesPayload.Change> changes = gitLabApiClient.getCommitDiff(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            commitSha,
            token
        );
        GitLabChangesPayload response = new GitLabChangesPayload();
        response.setTitle(title);
        response.setChanges(changes);
        log.info("gitlab commit diff fetched. projectId={}, commitSha={}, changes={}, elapsedMs={}",
            projectId, commitSha, changes == null ? 0 : changes.size(), elapsedMs(startNs));
        return response;
    }

    public String getRepositoryFileRaw(String gitlabProjectUrl, Long projectId,
                                       String filePath, String ref, String token) {
        long startNs = System.nanoTime();
        String content = gitLabApiClient.getRepositoryFileRaw(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            filePath,
            ref,
            token
        );
        log.info("gitlab repository file raw fetched. projectId={}, filePath={}, ref={}, bytes={}, elapsedMs={}",
            projectId, filePath, ref, content == null ? 0 : content.length(), elapsedMs(startNs));
        return content;
    }

    public void publishMergeRequestNote(Long projectId, String mergeRequestIid, String body) {
        publishMergeRequestNote(projectId, mergeRequestIid, body, null);
    }

    public void publishMergeRequestNote(Long projectId, String mergeRequestIid, String body, String token) {
        publishMergeRequestNote(null, projectId, mergeRequestIid, body, token);
    }

    public void publishMergeRequestNote(String gitlabProjectUrl, Long projectId,
                                        String mergeRequestIid, String body, String token) {
        gitLabApiClient.createMergeRequestNote(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            mergeRequestIid,
            new GitLabNoteRequest(body),
            token
        );
    }

    public void publishCommitNote(Long projectId, String commitSha, String body) {
        publishCommitNote(projectId, commitSha, body, null);
    }

    public void publishCommitNote(Long projectId, String commitSha, String body, String token) {
        publishCommitNote(null, projectId, commitSha, body, token);
    }

    public void publishCommitNote(String gitlabProjectUrl, Long projectId, String commitSha, String body, String token) {
        gitLabApiClient.createCommitNote(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            commitSha,
            new GitLabCommitNoteRequest(body),
            token
        );
    }

    private String resolveBaseUrl(String gitlabProjectUrl) {
        if (!StringUtils.hasText(gitlabProjectUrl)) {
            return null;
        }
        return gitLabProjectResolver.extractGitLabBaseUrl(gitlabProjectUrl);
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
