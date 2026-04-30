package com.vemo.codereview.webhook.support;

import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyBuilder {

    public String buildForMergeRequest(GitLabWebhookPayload payload) {
        String commitId = payload.getObjectAttributes().getLastCommit() == null
            ? "no-commit"
            : payload.getObjectAttributes().getLastCommit().getId();
        return new StringBuilder("gitlab")
            .append('-').append(payload.getProject().getId())
            .append('-').append(payload.getObjectKind())
            .append('-').append(payload.getObjectAttributes().getId())
            .append('-').append(commitId)
            .toString();
    }

    public String buildForPush(GitLabWebhookPayload payload) {
        String after = payload.getAfter() == null || payload.getAfter().trim().isEmpty()
            ? "no-after"
            : payload.getAfter();
        return buildForPushCommit(payload, after);
    }

    public String buildForPushCommit(GitLabWebhookPayload payload, String commitSha) {
        String resolvedCommitSha = commitSha == null || commitSha.trim().isEmpty()
            ? "no-commit"
            : commitSha.trim();
        return new StringBuilder("gitlab")
            .append('-').append(payload.getProject().getId())
            .append("-push-")
            .append(resolvedCommitSha)
            .toString();
    }
}
