package com.vemo.codereview.platform.gitlab.service;

import com.vemo.codereview.platform.gitlab.client.GitLabApiClient;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabComparePayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitNoteRequest;
import com.vemo.codereview.platform.gitlab.model.GitLabNoteRequest;
import com.vemo.codereview.project.service.GitLabProjectResolver;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;

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

    public GitLabChangesPayload getPushChanges(String gitlabProjectUrl, Long projectId,
                                               String before, String after, String branch,
                                               GitLabWebhookPayload push, String token) {
        String baseUrl = resolveBaseUrl(gitlabProjectUrl);
        if (isZeroSha(before)) {
            return getNewBranchPushChanges(baseUrl, projectId, after, branch, push, token);
        }
        GitLabComparePayload compare = gitLabApiClient.compare(
            baseUrl, projectId, before, after, token);
        return toChanges(branch, before, after, compare.getDiffs());
    }

    private GitLabChangesPayload getNewBranchPushChanges(String baseUrl, Long projectId, String after,
                                                         String branch, GitLabWebhookPayload push, String token) {
        List<GitLabWebhookPayload.Commit> webhookCommits = push == null || push.getCommits() == null
            ? Collections.<GitLabWebhookPayload.Commit>emptyList() : push.getCommits();
        int total = push != null && push.getTotalCommitsCount() != null
            ? push.getTotalCommitsCount() : webhookCommits.size();
        if (webhookCommits.isEmpty() || total <= 0) {
            throw new DomainException("PUSH_BASE_UNRESOLVED", "New branch push has no commit ancestry");
        }
        if (total == webhookCommits.size()) {
            String earliest = webhookCommits.get(0).getId();
            GitLabCommitPayload commit = gitLabApiClient.getCommit(baseUrl, projectId, earliest, token);
            if (commit != null && commit.getParentIds() != null && !commit.getParentIds().isEmpty()) {
                GitLabComparePayload compare = gitLabApiClient.compare(
                    baseUrl, projectId, commit.getParentIds().get(0), after, token);
                return toChanges(branch, commit.getParentIds().get(0), after, compare.getDiffs());
            }
            return buildRootPushChanges(baseUrl, projectId, branch, after, webhookCommits, token);
        }
        List<GitLabCommitPayload> reachable = gitLabApiClient.listReachableCommits(
            baseUrl, projectId, after, total + 1, token);
        if (reachable.size() < total + 1 || !matchesWebhookTail(reachable, webhookCommits)) {
            throw new DomainException("PUSH_BASE_UNRESOLVED", "Unable to resolve complete new branch ancestry");
        }
        String resolvedBase = reachable.get(total).getId();
        GitLabComparePayload compare = gitLabApiClient.compare(baseUrl, projectId, resolvedBase, after, token);
        return toChanges(branch, resolvedBase, after, compare.getDiffs());
    }

    private boolean matchesWebhookTail(List<GitLabCommitPayload> reachable,
                                       List<GitLabWebhookPayload.Commit> webhookCommits) {
        if (reachable.size() < webhookCommits.size()) {
            return false;
        }
        for (int index = 0; index < webhookCommits.size(); index++) {
            String expected = webhookCommits.get(webhookCommits.size() - 1 - index).getId();
            if (reachable.get(index) == null || !expected.equals(reachable.get(index).getId())) {
                return false;
            }
        }
        return true;
    }

    private GitLabChangesPayload buildRootPushChanges(String baseUrl, Long projectId, String branch, String after,
                                                      List<GitLabWebhookPayload.Commit> commits, String token) {
        Map<String, GitLabChangesPayload.Change> combined =
            new LinkedHashMap<String, GitLabChangesPayload.Change>();
        for (GitLabWebhookPayload.Commit commit : commits) {
            List<GitLabChangesPayload.Change> fragments = gitLabApiClient.getCommitDiff(
                baseUrl, projectId, commit.getId(), token);
            for (GitLabChangesPayload.Change fragment : fragments) {
                String path = StringUtils.hasText(fragment.getNewPath()) ? fragment.getNewPath() : fragment.getOldPath();
                GitLabChangesPayload.Change target = combined.get(path);
                if (target == null) {
                    target = new GitLabChangesPayload.Change();
                    target.setOldPath(fragment.getOldPath());
                    target.setNewPath(fragment.getNewPath());
                    target.setNewFile(fragment.getNewFile());
                    target.setDeletedFile(fragment.getDeletedFile());
                    target.setRenamedFile(fragment.getRenamedFile());
                    target.setDiff("");
                    combined.put(path, target);
                }
                target.setDiff(target.getDiff() + "# commit " + commit.getId() + "\n"
                    + (fragment.getDiff() == null ? "" : fragment.getDiff()) + "\n");
            }
        }
        log.warn("ROOT_PUSH_DIFF_FALLBACK projectId={}, after={}, commits={}", projectId, after, commits.size());
        return toChanges(branch, null, after, new ArrayList<GitLabChangesPayload.Change>(combined.values()));
    }

    private GitLabChangesPayload toChanges(String branch, String before, String after,
                                           List<GitLabChangesPayload.Change> diffs) {
        GitLabChangesPayload response = new GitLabChangesPayload();
        response.setTitle(branch + " " + before + ".." + after);
        response.setChanges(diffs);
        return response;
    }

    private boolean isZeroSha(String sha) {
        return StringUtils.hasText(sha) && sha.trim().matches("0+");
    }

    public List<GitLabCommitPayload> listBranchCommits(
        String gitlabProjectUrl,
        Long projectId,
        String branch,
        String since,
        String until,
        String token) {
        long startNs = System.nanoTime();
        List<GitLabCommitPayload> commits = gitLabApiClient.listRepositoryCommits(
            resolveBaseUrl(gitlabProjectUrl),
            projectId,
            branch,
            since,
            until,
            token
        );
        log.info("gitlab repository commits fetched. projectId={}, branch={}, commits={}, elapsedMs={}",
            projectId, branch, commits == null ? 0 : commits.size(), elapsedMs(startNs));
        return commits;
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
