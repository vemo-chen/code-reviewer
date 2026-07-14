package com.vemo.codereview.review.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ReviewResponseParser {

    private static final int DEFAULT_SUGGESTED_SCORE = 80;

    private final ObjectMapper objectMapper;

    public ReviewResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReviewSummary parse(ChatCompletionResponse response) {
        String content = response == null ? null : response.getFirstContent();
        log.debug("content={}", content);
        if (content == null || content.trim().isEmpty()) {
            throw new DomainException("REVIEW_RESULT_EMPTY", "Model response content is empty");
        }
        if (isLengthTruncated(response)) {
            throw new DomainException(
                "REVIEW_RESULT_TRUNCATED",
                "Model response was truncated before JSON completed. Increase max tokens or reduce prompt size."
            );
        }

        try {
            JsonNode root = readReviewJson(extractJson(content));
            ReviewSummary summary = new ReviewSummary();
            summary.setSuggestedScore(readScore(root, "suggestedScore", DEFAULT_SUGGESTED_SCORE));
            summary.setSummary(readText(root, "summary", "No summary provided"));
            summary.setBriefSummary(readText(root, "briefSummary", summary.getSummary()));
            summary.setRiskLevel(normalizeRiskLevel(readText(root, "riskLevel", "MEDIUM")));
            summary.setScoreReason(readText(root, "scoreReason", null));
            summary.setAdvice(readText(root, "advice", null));
            summary.setComments(parseComments(root.path("comments")));
            return summary;
        } catch (Exception ex) {
            throw new DomainException(
                "REVIEW_RESULT_PARSE_ERROR",
                "Failed to parse review result JSON. content=" + abbreviate(content)
            );
        }
    }

    private JsonNode readReviewJson(String json) throws JsonProcessingException {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return objectMapper.readTree(repairJsonStringLiterals(json));
        }
    }

    private boolean isLengthTruncated(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return false;
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        return choice != null && "length".equalsIgnoreCase(choice.getFinishReason());
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak >= 0) {
                trimmed = trimmed.substring(firstLineBreak + 1).trim();
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.lastIndexOf("```")).trim();
            }
        }

        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return trimmed;
    }

    private String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500) + "...";
    }

    private String repairJsonStringLiterals(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder repaired = new StringBuilder(json.length() + 32);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (!inString) {
                if (current == '"') {
                    inString = true;
                }
                repaired.append(current);
                continue;
            }
            if (escaped) {
                repaired.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                repaired.append(current);
                escaped = true;
                continue;
            }
            if (current == '"') {
                if (isLikelyClosingQuote(json, i)) {
                    inString = false;
                    repaired.append(current);
                } else {
                    repaired.append("\\\"");
                }
                continue;
            }
            if (current == '\n') {
                repaired.append("\\n");
                continue;
            }
            if (current == '\r') {
                repaired.append("\\r");
                continue;
            }
            if (current == '\t') {
                repaired.append("\\t");
                continue;
            }
            repaired.append(current);
        }
        return repaired.toString();
    }

    private boolean isLikelyClosingQuote(String json, int quoteIndex) {
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char next = json.charAt(i);
            if (Character.isWhitespace(next)) {
                continue;
            }
            return next == ':' || next == ',' || next == '}' || next == ']';
        }
        return true;
    }

    private List<ReviewCommentDraft> parseComments(JsonNode commentsNode) {
        List<ReviewCommentDraft> comments = new ArrayList<ReviewCommentDraft>();
        if (commentsNode == null || !commentsNode.isArray()) {
            return comments;
        }

        for (JsonNode commentNode : commentsNode) {
            ReviewCommentDraft draft = new ReviewCommentDraft();
            draft.setFilePath(readText(commentNode, "filePath", "UNKNOWN"));
            draft.setLine(commentNode.path("line").isInt() ? commentNode.path("line").asInt() : null);
            draft.setSeverity(normalizeSeverity(readText(commentNode, "severity", "MEDIUM")));
            draft.setCategory(normalizeCategory(readText(commentNode, "category", "General")));
            draft.setMessage(readText(commentNode, "message", ""));
            draft.setSuggestion(readText(commentNode, "suggestion", null));
            draft.setSuggestedCode(readText(commentNode, "suggestedCode", null));
            draft.setCodeStartLine(commentNode.path("codeStartLine").isInt() ? commentNode.path("codeStartLine").asInt() : null);
            draft.setCodeEndLine(commentNode.path("codeEndLine").isInt() ? commentNode.path("codeEndLine").asInt() : null);
            draft.setEvidenceType(normalizeEvidenceType(readText(commentNode, "evidenceType", "DIFF_ONLY")));
            draft.setConfidence(normalizeConfidence(readText(commentNode, "confidence", "MEDIUM")));
            draft.setCommentHash(buildCommentHash(draft));
            comments.add(draft);
        }
        return comments;
    }

    private String readText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return defaultValue;
        }
        String value = field.asText();
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private Integer readScore(JsonNode node, String fieldName, int defaultValue) {
        JsonNode field = node.path(fieldName);
        if (field.isInt() || field.isLong()) {
            return clampScore(field.asInt());
        }
        if (field.isTextual()) {
            try {
                return clampScore(Integer.parseInt(field.asText().trim()));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String normalizeRiskLevel(String value) {
        return normalizeEnum(value, "LOW", "MEDIUM", "HIGH", "CRITICAL");
    }

    private String normalizeSeverity(String value) {
        return normalizeEnum(value, "LOW", "MEDIUM", "HIGH", "CRITICAL");
    }

    private String normalizeEvidenceType(String value) {
        return normalizeEnum(value, "DIFF_ONLY", "DIFF_WITH_CONTEXT", "NEEDS_CONFIRMATION");
    }

    private String normalizeConfidence(String value) {
        return normalizeEnum(value, "LOW", "MEDIUM", "HIGH");
    }

    private String normalizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return "General";
        }
        String normalized = value.trim();
        String upper = normalized.toUpperCase();
        if ("CORRECTNESS".equals(upper) || "FUNCTIONALITY".equals(upper)) {
            return "Correctness";
        }
        if ("SECURITY".equals(upper)) {
            return "Security";
        }
        if ("MAINTAINABILITY".equals(upper) || "MAINTAINABLE".equals(upper)) {
            return "Maintainability";
        }
        if ("PERFORMANCE".equals(upper)) {
            return "Performance";
        }
        if ("PROJECT_HARD_RULE".equals(upper) || "HARD_RULE".equals(upper) || "HARD_RULES".equals(upper)) {
            return "Project hard rule";
        }
        if ("GENERAL".equals(upper) || "OTHER".equals(upper)) {
            return "General";
        }
        return normalized;
    }

    private String normalizeEnum(String value, String... allowed) {
        if (value == null) {
            return "MEDIUM";
        }
        String normalized = value.trim().toUpperCase();
        for (String item : allowed) {
            if (item.equals(normalized)) {
                return normalized;
            }
        }
        return "MEDIUM";
    }

    private String buildCommentHash(ReviewCommentDraft draft) {
        String raw = new StringBuilder()
            .append(draft.getFilePath()).append('|')
            .append(draft.getLine()).append('|')
            .append(draft.getSeverity()).append('|')
            .append(draft.getCategory()).append('|')
            .append(draft.getMessage()).append('|')
            .append(draft.getSuggestion())
            .toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : bytes) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new DomainException("REVIEW_HASH_ERROR", "Failed to generate comment hash");
        }
    }
}
