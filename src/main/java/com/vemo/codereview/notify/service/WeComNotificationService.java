package com.vemo.codereview.notify.service;

import com.vemo.codereview.notify.client.WeComWebhookClient;
import com.vemo.codereview.notify.model.ReviewNotificationMetadata;
import com.vemo.codereview.notify.model.WeComMarkdownPayload;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeComNotificationService {

    private final WeComWebhookClient weComWebhookClient;

    public WeComNotificationService(WeComWebhookClient weComWebhookClient) {
        this.weComWebhookClient = weComWebhookClient;
    }

    public boolean notifyReviewResult(
        Long projectId,
        ReviewNotificationMetadata metadata,
        CodeReviewResultEntity result,
        List<CodeReviewCommentEntity> comments) {
        return notifyReviewResult(projectId, metadata, result, comments, null);
    }

    public boolean notifyReviewResult(
        Long projectId,
        ReviewNotificationMetadata metadata,
        CodeReviewResultEntity result,
        List<CodeReviewCommentEntity> comments,
        String webhookUrlOverride) {
        if (!StringUtils.hasText(webhookUrlOverride)) {
            return false;
        }
        if (result == null) {
            return false;
        }

        WeComMarkdownPayload message = new WeComMarkdownPayload();
        WeComMarkdownPayload.MarkdownPayload markdown = new WeComMarkdownPayload.MarkdownPayload();
        markdown.setContent(buildReviewMarkdown(projectId, metadata, result, comments));
        message.setMarkdown(markdown);
        weComWebhookClient.sendMarkdown(webhookUrlOverride, message);
        return true;
    }

    public boolean notifyDailyReport(String content) {
        return false;
    }

    public String previewReviewMarkdown(
        Long projectId,
        ReviewNotificationMetadata metadata,
        CodeReviewResultEntity result,
        List<CodeReviewCommentEntity> comments) {
        if (result == null) {
            return "";
        }
        return buildReviewMarkdown(projectId, metadata, result, comments);
    }

    private String buildReviewMarkdown(
        Long projectId,
        ReviewNotificationMetadata metadata,
        CodeReviewResultEntity result,
        List<CodeReviewCommentEntity> comments) {
        StringBuilder builder = new StringBuilder();
        builder.append("## AI Code Review\n");
        builder.append("> Project ID: <font color=\"comment\">").append(projectId).append("</font>\n");
        builder.append("> ").append(resolveTargetLabel(metadata == null ? null : metadata.getReviewTargetType()))
            .append(": <font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getTargetId()))
            .append("</font>\n");
        builder.append("> Submit message: <font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitMessage()))
            .append("</font>\n");
        builder.append("> Submitter: <font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitter()))
            .append("</font>\n");
        builder.append("> Branch: <font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitBranch()))
            .append("</font>\n");
        builder.append("> Submitted at: <font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitTime()))
            .append("</font>\n");
        builder.append("> Risk: ").append(formatSeverity(result.getRiskLevel())).append("\n");
        builder.append("> Score: <font color=\"comment\">")
            .append(safeScore(resolveFinalScore(result))).append("</font>\n");
        if (StringUtils.hasText(result.getScoreReason())) {
            builder.append("> Score reason: ").append(safe(result.getScoreReason())).append("\n");
        }
        builder.append("> Summary: ").append(safe(resolveDisplayedSummary(result))).append("\n");
        if (StringUtils.hasText(result.getAdvice())) {
            builder.append("> Advice: ").append(safe(result.getAdvice())).append("\n");
        }
        builder.append("> Issue count: <font color=\"comment\">")
            .append(comments == null ? 0 : comments.size())
            .append("</font>\n\n");

        if (comments == null || comments.isEmpty()) {
            builder.append("No parsed issues.\n\n");
        } else {
            int limit = Math.min(comments.size(), 3);
            builder.append("**Top issues (first ").append(limit).append(")**\n");
            for (int i = 0; i < limit; i++) {
                CodeReviewCommentEntity comment = comments.get(i);
                builder.append("**")
                    .append(i + 1)
                    .append(". ")
                    .append(safe(comment.getCategory()))
                    .append("[")
                    .append(formatSeverity(comment.getSeverity()))
                    .append("]**\n");
                builder.append("   **Location:** ").append(safe(comment.getFilePath()));
                if (comment.getLineNo() != null) {
                    builder.append(":").append(comment.getLineNo());
                }
                builder.append("\n");
                builder.append("   **Issue:** ").append(safe(comment.getMessage())).append("\n");
                if (StringUtils.hasText(comment.getSuggestion())) {
                    builder.append("   **Suggestion:** ").append(safe(comment.getSuggestion())).append("\n");
                }
            }
            if (comments.size() > limit) {
                builder.append("\nThere are ")
                    .append(comments.size() - limit)
                    .append(" more issues. ");
            }
            String platformUrl = "http://localhost:5173/dashboard";
            builder.append("Open [Code Reviewer](")
                .append(platformUrl)
                .append(") for details.\n");
            builder.append("\n");
        }

        if (StringUtils.hasText(result.getSummary())) {
            builder.append("**Full summary**\n");
            builder.append(safe(result.getSummary()));
        }
        return builder.toString();
    }

    private String resolveDisplayedSummary(CodeReviewResultEntity result) {
        if (result == null) {
            return "-";
        }
        if (StringUtils.hasText(result.getBriefSummary())) {
            return result.getBriefSummary();
        }
        return result.getSummary();
    }

    private Integer resolveFinalScore(CodeReviewResultEntity result) {
        if (result == null) {
            return null;
        }
        if (result.getFinalScore() != null) {
            return result.getFinalScore();
        }
        return result.getSuggestedScore();
    }

    private String resolveTargetLabel(String reviewTargetType) {
        if ("PUSH_REVIEW".equalsIgnoreCase(reviewTargetType) || "commit".equalsIgnoreCase(reviewTargetType)) {
            return "Commit SHA";
        }
        if ("MR_REVIEW".equalsIgnoreCase(reviewTargetType) || "merge_request".equalsIgnoreCase(reviewTargetType)) {
            return "Merge Request IID";
        }
        return "閻╊喗鐖D";
    }

    private String formatSeverity(String level) {
        if (!StringUtils.hasText(level)) {
            return "<font color=\"comment\">-</font>";
        }
        String normalized = level.trim().toUpperCase();
        if ("CRITICAL".equals(normalized) || "HIGH".equals(normalized)) {
            return "`" + safe(normalized) + "`";
        }
        if ("MEDIUM".equals(normalized)) {
            return "<font color=\"warning\">" + safe(normalized) + "</font>";
        }
        if ("LOW".equals(normalized)) {
            return "<font color=\"info\">" + safe(normalized) + "</font>";
        }
        return "<font color=\"comment\">" + safe(normalized) + "</font>";
    }

    private String safe(String value) {
        return value == null ? "-" : value.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String safeScore(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
