package com.vemo.codereview.review.service;

import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewScoreService {

    private static final int DEFAULT_SUGGESTED_SCORE = 80;

    public void applyScores(ReviewSummary summary) {
        if (summary == null) {
            return;
        }

        int suggestedScore = clampScore(summary.getSuggestedScore());
        if (summary.getSuggestedScore() == null) {
            suggestedScore = DEFAULT_SUGGESTED_SCORE;
        }
        int deductionScore = calculateHardRuleDeduction(summary.getComments());
        int finalScore = Math.max(0, suggestedScore - deductionScore);

        summary.setSuggestedScore(suggestedScore);
        summary.setDeductionScore(deductionScore);
        summary.setFinalScore(finalScore);
        summary.setScoreReason(buildScoreReason(summary.getComments(), suggestedScore, deductionScore, finalScore));
    }

    private int calculateHardRuleDeduction(List<ReviewCommentDraft> comments) {
        if (comments == null || comments.isEmpty()) {
            return 0;
        }
        Set<String> deductedProblemTypes = new LinkedHashSet<String>();
        int total = 0;
        for (ReviewCommentDraft comment : comments) {
            if (!isHardRuleViolation(comment)) {
                continue;
            }
            String problemTypeKey = buildProblemTypeKey(comment);
            if (!deductedProblemTypes.add(problemTypeKey)) {
                continue;
            }
            total += resolveDeduction(comment.getSeverity());
        }
        return total;
    }

    private String buildScoreReason(List<ReviewCommentDraft> comments, int suggestedScore, int deductionScore, int finalScore) {
        List<String> parts = new ArrayList<String>();
        parts.add("模型建议分: " + suggestedScore);

        if (comments == null || comments.isEmpty() || deductionScore <= 0) {
            parts.add("未命中项目硬性规则扣分");
            parts.add("最终得分: " + finalScore);
            return String.join("；", parts);
        }

        Map<String, Integer> countsBySeverity = new LinkedHashMap<String, Integer>();
        Set<String> deductedProblemTypes = new LinkedHashSet<String>();
        for (ReviewCommentDraft comment : comments) {
            if (!isHardRuleViolation(comment)) {
                continue;
            }
            String problemTypeKey = buildProblemTypeKey(comment);
            if (!deductedProblemTypes.add(problemTypeKey)) {
                continue;
            }
            String severity = normalizeSeverity(comment.getSeverity());
            Integer count = countsBySeverity.get(severity);
            countsBySeverity.put(severity, count == null ? 1 : count + 1);
        }

        if (countsBySeverity.isEmpty()) {
            parts.add("未命中项目硬性规则扣分");
            parts.add("最终得分: " + finalScore);
            return String.join("；", parts);
        }

        for (Map.Entry<String, Integer> entry : countsBySeverity.entrySet()) {
            int count = entry.getValue();
            int amount = resolveDeduction(entry.getKey()) * count;
            parts.add("命中 " + count + " 个" + toChineseSeverity(entry.getKey()) + "级项目硬性规则问题，扣 " + amount + " 分");
        }
        parts.add("最终得分: " + finalScore);
        return String.join("；", parts);
    }

    private boolean isHardRuleViolation(ReviewCommentDraft comment) {
        if (comment == null || !StringUtils.hasText(comment.getCategory())) {
            return false;
        }
        return comment.getCategory().toUpperCase(Locale.ROOT).contains("HARD_RULE") || comment.getCategory().contains("Project hard rule");
    }

    private String buildProblemTypeKey(ReviewCommentDraft comment) {
        String category = normalizeText(comment.getCategory());
        String message = normalizeText(comment.getMessage());
        return category + "|" + message;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replaceAll("\\s+", "")
            .replace('，', ':')
            .toLowerCase(Locale.ROOT);
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return DEFAULT_SUGGESTED_SCORE;
        }
        return Math.max(0, Math.min(100, score));
    }

    private int resolveDeduction(String severity) {
        String normalized = normalizeSeverity(severity);
        if ("CRITICAL".equals(normalized)) {
            return 10;
        }
        if ("HIGH".equals(normalized)) {
            return 6;
        }
        if ("LOW".equals(normalized)) {
            return 2;
        }
        return 4;
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "MEDIUM";
        }
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        if ("CRITICAL".equals(normalized) || "HIGH".equals(normalized)
            || "MEDIUM".equals(normalized) || "LOW".equals(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }

    private String toChineseSeverity(String severity) {
        String normalized = normalizeSeverity(severity);
        if ("CRITICAL".equals(normalized)) {
            return "严重";
        }
        if ("HIGH".equals(normalized)) {
            return "高";
        }
        if ("LOW".equals(normalized)) {
            return "低";
        }
        return "中";
    }
}
