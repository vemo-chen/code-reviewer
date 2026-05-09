package com.vemo.codereview.review.service;

import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitPayload;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ManualReviewEventFactory {

    private final ObjectMapper objectMapper;

    public ManualReviewEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CodeReviewEventEntity buildCommitEvent(
        ProjectProfileEntity project,
        GitLabCommitPayload commit,
        String submitBranch,
        Long operatorUserId,
        String operatorName,
        Date now) {
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform(project == null || !StringUtils.hasText(project.getSourcePlatform())
            ? "gitlab" : project.getSourcePlatform().trim());
        event.setEventType("manual_commit_review");
        event.setProjectId(project == null ? null : project.getId());
        event.setProjectName(project == null ? null : project.getProjectName());
        event.setObjectId(commit == null ? null : normalize(commit.getId()));
        event.setObjectType("commit");
        event.setOperatorId(resolveSubmitterId(commit));
        event.setOperatorName(resolveSubmitterName(commit));
        event.setSubmitBranch(normalize(submitBranch));
        event.setSubmitTime(resolveSubmitTime(commit));
        event.setIdempotentKey(buildIdempotentKey(project == null ? null : project.getId(), commit == null ? null : commit.getId()));
        event.setPayloadJson(buildPayloadJson(project, commit, submitBranch, operatorUserId, operatorName));
        event.setStatus(ReviewEventLifecycle.RECEIVED.name());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private String buildIdempotentKey(Long projectId, String commitSha) {
        return new StringBuilder("manual")
            .append('-')
            .append(projectId == null ? "no-project" : projectId)
            .append("-push-")
            .append(StringUtils.hasText(commitSha) ? commitSha.trim() : "no-commit")
            .toString();
    }

    private String buildPayloadJson(
        ProjectProfileEntity project,
        GitLabCommitPayload commit,
        String submitBranch,
        Long operatorUserId,
        String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("object_kind", "manual_commit_review");
        payload.put("event_type", "manual_commit_review");
        payload.put("user_name", resolveSubmitterName(commit));
        payload.put("user_username", resolveSubmitterId(commit));
        payload.put("ref", StringUtils.hasText(submitBranch) ? "refs/heads/" + submitBranch.trim() : null);
        payload.put("manual_operator_id", operatorUserId);
        payload.put("manual_operator_name", normalize(operatorName));

        if (project != null && project.getGitlabProjectId() != null) {
            Map<String, Object> projectNode = new LinkedHashMap<String, Object>();
            projectNode.put("id", project.getGitlabProjectId());
            projectNode.put("name", project.getProjectName());
            payload.put("project", projectNode);
        }

        List<Map<String, Object>> commits = new ArrayList<Map<String, Object>>();
        if (commit != null && StringUtils.hasText(commit.getId())) {
            Map<String, Object> commitNode = new LinkedHashMap<String, Object>();
            commitNode.put("id", normalize(commit.getId()));
            commitNode.put("title", normalize(commit.getTitle()));
            commitNode.put("message", normalize(commit.getMessage()));
            commitNode.put("timestamp", normalize(commit.getCommittedDate()));
            commits.add(commitNode);
        }
        payload.put("commits", commits);
        payload.put("after", commit == null ? null : normalize(commit.getId()));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize manual review payload", ex);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Date resolveSubmitTime(GitLabCommitPayload commit) {
        if (commit == null || !StringUtils.hasText(commit.getCommittedDate())) {
            return null;
        }
        String value = commit.getCommittedDate().trim();
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

    private String resolveSubmitterId(GitLabCommitPayload commit) {
        return normalize(commit == null ? null : commit.getAuthorName());
    }

    private String resolveSubmitterName(GitLabCommitPayload commit) {
        return normalize(commit == null ? null : commit.getAuthorName());
    }
}
