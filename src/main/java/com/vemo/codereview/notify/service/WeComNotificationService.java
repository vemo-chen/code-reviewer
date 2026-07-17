package com.vemo.codereview.notify.service;

import com.vemo.codereview.common.config.AppProperties;
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
    private final AppProperties appProperties;

    public WeComNotificationService(WeComWebhookClient weComWebhookClient, AppProperties appProperties) {
        this.weComWebhookClient = weComWebhookClient;
        this.appProperties = appProperties;
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
        builder.append("> 项目ID：<font color=\"comment\">").append(projectId).append("</font>\n");
        builder.append("> ").append(resolveTargetLabel(metadata == null ? null : metadata.getReviewTargetType()))
            .append("：<font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getTargetId()))
            .append("</font>\n");
        builder.append("> 提交信息：<font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitMessage()))
            .append("</font>\n");
        builder.append("> 提交者：<font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitter()))
            .append("</font>\n");
        builder.append("> 提交分支：<font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitBranch()))
            .append("</font>\n");
        builder.append("> 提交时间：<font color=\"comment\">")
            .append(safe(metadata == null ? null : metadata.getSubmitTime()))
            .append("</font>\n");
        if (metadata != null && StringUtils.hasText(metadata.getAfterSha())) {
            builder.append("> Push 范围：<font color=\"comment\">")
                .append(safe(metadata.getPushBranch())).append(" ")
                .append(safe(metadata.getBeforeSha())).append("..")
                .append(safe(metadata.getAfterSha())).append(" (")
                .append(metadata.getCommitCount() == null ? 0 : metadata.getCommitCount())
                .append(" commits)</font>\n");
        }
        if (metadata != null && StringUtils.hasText(metadata.getGitlabUrl())) {
            builder.append("> GitLab：[打开审查对象](")
                .append(metadata.getGitlabUrl()).append(")\n");
        }
        builder.append("> 风险等级：").append(formatSeverity(result.getRiskLevel())).append("\n");
        builder.append("> 最终得分：<font color=\"comment\">")
            .append(safeScore(resolveFinalScore(result))).append("</font>\n");
        if (StringUtils.hasText(result.getScoreReason())) {
            builder.append("> 得分说明：").append(safe(result.getScoreReason())).append("\n");
        }
        builder.append("> 审查摘要：").append(safe(resolveDisplayedSummary(result))).append("\n");
        if (StringUtils.hasText(result.getAdvice())) {
            builder.append("> 建议：").append(safe(result.getAdvice())).append("\n");
        }
        builder.append("> 问题总数：<font color=\"comment\">")
            .append(comments == null ? 0 : comments.size())
            .append("</font>\n\n");

        if (comments == null || comments.isEmpty()) {
            builder.append("未解析到问题。\n\n");
        } else {
            int limit = Math.min(comments.size(), 3);
            builder.append("**重点问题（前").append(limit).append("条）**\n");
            for (int i = 0; i < limit; i++) {
                CodeReviewCommentEntity comment = comments.get(i);
                builder.append("**")
                    .append(i + 1)
                    .append(". ")
                    .append(safe(comment.getCategory()))
                    .append("[")
                    .append(formatSeverity(comment.getSeverity()))
                    .append("]**\n");
                builder.append("   **位置：**").append(safe(comment.getFilePath()));
                if (comment.getLineNo() != null) {
                    builder.append(":").append(comment.getLineNo());
                }
                builder.append("\n");
                builder.append("   **问题：**").append(safe(comment.getMessage())).append("\n");
                if (StringUtils.hasText(comment.getSuggestion())) {
                    builder.append("   **建议：**").append(safe(comment.getSuggestion())).append("\n");
                }
            }
            if (comments.size() > limit) {
                builder.append("\n其余还有 ")
                    .append(comments.size() - limit)
                    .append(" 条问题。");
            }
            String platformUrl = appProperties.getPlatformUrl();
            builder.append("请到[代码审查平台](")
                .append(platformUrl)
                .append(")查看并操作。\n");
            builder.append("\n");
        }

        if (StringUtils.hasText(result.getSummary())) {
            builder.append("**总结**\n");
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
        return "目标ID";
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
