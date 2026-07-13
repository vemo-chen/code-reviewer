package com.vemo.codereview.webhook.support;

import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyBuilder {

    public String buildForMergeRequest(GitLabWebhookPayload payload) {
        return new StringBuilder("gitlab")
            .append('-').append(payload.getProject().getId())
            .append('-').append(payload.getObjectKind())
            .append('-').append(payload.getObjectAttributes().getId())
            .toString();
    }

    public String buildForPush(GitLabWebhookPayload payload) {
        String canonical = payload.getProject().getId() + "\n"
            + normalizeBranch(payload.getRef()) + "\n"
            + normalizeSha(payload.getBefore()) + "\n"
            + normalizeSha(payload.getAfter());
        return "gitlab-push-" + sha256Hex(canonical);
    }

    private String normalizeBranch(String ref) {
        String normalized = ref == null ? "" : ref.trim();
        return normalized.startsWith("refs/heads/") ? normalized.substring("refs/heads/".length()) : normalized;
    }

    private String normalizeSha(String sha) {
        return sha == null ? "" : sha.trim().toLowerCase();
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
