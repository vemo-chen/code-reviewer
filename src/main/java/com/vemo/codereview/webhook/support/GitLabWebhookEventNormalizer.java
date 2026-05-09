package com.vemo.codereview.webhook.support;

import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.model.StandardReviewEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitLabWebhookEventNormalizer {

    private final ObjectMapper objectMapper;
    private final IdempotencyKeyBuilder idempotencyKeyBuilder;

    public GitLabWebhookEventNormalizer(ObjectMapper objectMapper, IdempotencyKeyBuilder idempotencyKeyBuilder) {
        this.objectMapper = objectMapper;
        this.idempotencyKeyBuilder = idempotencyKeyBuilder;
    }

    public StandardReviewEvent normalizeMergeRequest(GitLabWebhookPayload payload) {
        StandardReviewEvent event = new StandardReviewEvent();
        event.setSourcePlatform("gitlab");
        event.setEventType(payload.getObjectKind());
        event.setProjectId(payload.getProject().getId());
        event.setProjectName(payload.getProject().getName());
        event.setObjectId(String.valueOf(payload.getObjectAttributes().getId()));
        event.setObjectType("merge_request");
        event.setOperatorId(payload.getUser() == null ? null : String.valueOf(payload.getUser().getId()));
        event.setOperatorName(payload.getUser() == null ? null : payload.getUser().getName());
        event.setSubmitBranch(payload.getObjectAttributes() == null ? null : payload.getObjectAttributes().getSourceBranch());
        event.setSubmitTime(resolveMergeRequestSubmitTime(payload));
        event.setTargetId(String.valueOf(payload.getObjectAttributes().getIid()));
        event.setTargetTitle(payload.getObjectAttributes().getTitle());
        event.setIdempotentKey(idempotencyKeyBuilder.buildForMergeRequest(payload));
        event.setPayloadJson(writePayload(payload));
        return event;
    }

    public StandardReviewEvent normalizePush(GitLabWebhookPayload payload) {
        String commitSha = payload.getAfter();
        String title = resolvePushTitle(payload);
        return normalizePushCommit(payload, commitSha, title);
    }

    public List<StandardReviewEvent> normalizePushCommits(GitLabWebhookPayload payload) {
        List<StandardReviewEvent> events = new ArrayList<StandardReviewEvent>();
        List<GitLabWebhookPayload.Commit> commits = payload.getCommits();
        if (commits == null || commits.isEmpty()) {
            events.add(normalizePush(payload));
            return events;
        }
        for (GitLabWebhookPayload.Commit commit : commits) {
            if (commit == null) {
                continue;
            }
            String commitSha = StringUtils.hasText(commit.getId()) ? commit.getId().trim() : payload.getAfter();
            if (!StringUtils.hasText(commitSha)) {
                continue;
            }
            events.add(normalizePushCommit(payload, commitSha, resolveCommitTitle(commit)));
        }
        if (events.isEmpty()) {
            events.add(normalizePush(payload));
        }
        return events;
    }

    private StandardReviewEvent normalizePushCommit(GitLabWebhookPayload payload, String commitSha, String title) {
        StandardReviewEvent event = new StandardReviewEvent();
        event.setSourcePlatform("gitlab");
        event.setEventType(payload.getObjectKind());
        event.setProjectId(payload.getProject().getId());
        event.setProjectName(payload.getProject().getName());
        event.setObjectId(commitSha);
        event.setObjectType("commit");
        event.setOperatorId(payload.getUserUsername());
        event.setOperatorName(payload.getUserName());
        event.setSubmitBranch(resolvePushBranch(payload.getRef()));
        event.setSubmitTime(resolveCommitSubmitTime(payload, commitSha));
        event.setTargetId(commitSha);
        event.setTargetTitle(title);
        event.setIdempotentKey(idempotencyKeyBuilder.buildForPushCommit(payload, commitSha));
        event.setPayloadJson(writePayload(payload));
        return event;
    }

    private String resolvePushTitle(GitLabWebhookPayload payload) {
        List<GitLabWebhookPayload.Commit> commits = payload.getCommits();
        if (commits != null && !commits.isEmpty()) {
            GitLabWebhookPayload.Commit latestCommit = commits.get(commits.size() - 1);
            if (latestCommit != null) {
                if (latestCommit.getTitle() != null && !latestCommit.getTitle().trim().isEmpty()) {
                    return latestCommit.getTitle().trim();
                }
                if (latestCommit.getMessage() != null && !latestCommit.getMessage().trim().isEmpty()) {
                    String message = latestCommit.getMessage().trim();
                    int newLine = message.indexOf('\n');
                    return newLine > 0 ? message.substring(0, newLine).trim() : message;
                }
            }
        }
        return payload.getRef() == null ? "Push Review" : payload.getRef();
    }

    private String resolveCommitTitle(GitLabWebhookPayload.Commit commit) {
        if (commit == null) {
            return null;
        }
        if (StringUtils.hasText(commit.getTitle())) {
            return commit.getTitle().trim();
        }
        if (StringUtils.hasText(commit.getMessage())) {
            String message = commit.getMessage().trim();
            int newLine = message.indexOf('\n');
            return newLine > 0 ? message.substring(0, newLine).trim() : message;
        }
        return commit.getId();
    }

    private String resolvePushBranch(String ref) {
        if (ref == null || ref.trim().isEmpty()) {
            return null;
        }
        String normalized = ref.trim();
        String headPrefix = "refs/heads/";
        if (normalized.startsWith(headPrefix)) {
            return normalized.substring(headPrefix.length());
        }
        return normalized;
    }

    private Date resolveMergeRequestSubmitTime(GitLabWebhookPayload payload) {
        if (payload == null || payload.getObjectAttributes() == null) {
            return null;
        }
        Date submitTime = parseTimestamp(payload.getObjectAttributes().getCreatedAt());
        if (submitTime != null) {
            return submitTime;
        }
        submitTime = parseTimestamp(payload.getObjectAttributes().getUpdatedAt());
        if (submitTime != null) {
            return submitTime;
        }
        return payload.getObjectAttributes().getLastCommit() == null
            ? null : parseTimestamp(payload.getObjectAttributes().getLastCommit().getTimestamp());
    }

    private Date resolveCommitSubmitTime(GitLabWebhookPayload payload, String commitSha) {
        if (payload == null || payload.getCommits() == null || payload.getCommits().isEmpty()) {
            return null;
        }
        for (GitLabWebhookPayload.Commit commit : payload.getCommits()) {
            if (commit == null || !StringUtils.hasText(commit.getId())) {
                continue;
            }
            if (StringUtils.hasText(commitSha) && commitSha.trim().equals(commit.getId().trim())) {
                Date submitTime = parseTimestamp(commit.getTimestamp());
                if (submitTime != null) {
                    return submitTime;
                }
            }
        }
        for (GitLabWebhookPayload.Commit commit : payload.getCommits()) {
            if (commit == null) {
                continue;
            }
            Date submitTime = parseTimestamp(commit.getTimestamp());
            if (submitTime != null) {
                return submitTime;
            }
        }
        return null;
    }

    private Date parseTimestamp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(Instant.parse(value));
        } catch (Exception ignored) {
        }
        return null;
    }

    private String writePayload(GitLabWebhookPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize webhook payload", ex);
        }
    }
}
