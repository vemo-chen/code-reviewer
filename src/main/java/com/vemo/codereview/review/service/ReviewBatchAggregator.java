package com.vemo.codereview.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.review.model.AggregatedReviewOutput;
import com.vemo.codereview.review.model.ReviewBatchOutput;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewBatchAggregator {
    private static final int RAW_LIMIT = 60000;
    private final ReviewScoreService scoreService;
    private final ObjectMapper objectMapper;

    public ReviewBatchAggregator(ReviewScoreService scoreService, ObjectMapper objectMapper) {
        this.scoreService = scoreService;
        this.objectMapper = objectMapper;
    }

    public AggregatedReviewOutput aggregate(List<ReviewBatchOutput> outputs) {
        ReviewSummary summary = new ReviewSummary();
        List<ReviewCommentDraft> comments = new ArrayList<ReviewCommentDraft>();
        Set<String> hashes = new HashSet<String>();
        List<String> summaries = new ArrayList<String>();
        List<String> advice = new ArrayList<String>();
        Integer score = null;
        String risk = "LOW";
        String brief = null;
        int input = 0;
        int outputTokens = 0;
        long latency = 0;
        String model = null;
        for (ReviewBatchOutput item : outputs) {
            ReviewSummary current = item.getSummary();
            if (current.getSuggestedScore() != null) score = score == null
                ? current.getSuggestedScore() : Math.min(score, current.getSuggestedScore());
            if (rank(current.getRiskLevel()) > rank(risk)) {
                risk = current.getRiskLevel();
                brief = current.getBriefSummary();
            }
            addDistinct(summaries, current.getSummary());
            addDistinct(advice, current.getAdvice());
            if (current.getComments() != null) for (ReviewCommentDraft comment : current.getComments()) {
                String hash = StringUtils.hasText(comment.getCommentHash()) ? comment.getCommentHash() : hash(comment);
                comment.setCommentHash(hash);
                if (hashes.add(hash)) comments.add(comment);
            }
            if (item.getResponse() != null) {
                model = item.getResponse().getModel();
                if (item.getResponse().getUsage() != null) {
                    input += value(item.getResponse().getUsage().getPromptTokens());
                    outputTokens += value(item.getResponse().getUsage().getCompletionTokens());
                }
            }
            latency += item.getLatencyMs();
        }
        summary.setSuggestedScore(score);
        summary.setRiskLevel(risk);
        summary.setBriefSummary(brief == null && !summaries.isEmpty() ? summaries.get(0) : brief);
        summary.setSummary(joinWithin(summaries, 30000));
        summary.setAdvice(joinWithin(advice, 30000));
        summary.setComments(comments);
        if (!outputs.isEmpty()) scoreService.applyScores(summary);
        AggregatedReviewOutput result = new AggregatedReviewOutput();
        result.setSummary(summary);
        result.setProviderName("openai-compatible");
        result.setModelName(model);
        result.setInputTokens(input);
        result.setOutputTokens(outputTokens);
        result.setLatencyMs(latency);
        result.setRawResponse(rawTrace(outputs));
        return result;
    }

    private String rawTrace(List<ReviewBatchOutput> outputs) {
        List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
        for (ReviewBatchOutput output : outputs) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("batchIndex", output.getBatchIndex());
            item.put("content", output.getRawContent());
            item.put("truncated", false);
            trace.add(item);
        }
        String json = write(trace);
        while (json.getBytes(StandardCharsets.UTF_8).length > RAW_LIMIT) {
            Map<String, Object> largest = null;
            int largestBytes = 0;
            for (Map<String, Object> item : trace) {
                String content = String.valueOf(item.get("content"));
                int bytes = content.getBytes(StandardCharsets.UTF_8).length;
                if (bytes > largestBytes) { largest = item; largestBytes = bytes; }
            }
            if (largest == null || largestBytes == 0) break;
            String content = String.valueOf(largest.get("content"));
            int codePoints = content.codePointCount(0, content.length());
            int keep = Math.max(0, codePoints - Math.max(1, codePoints / 4));
            largest.put("content", content.substring(0, content.offsetByCodePoints(0, keep)));
            largest.put("truncated", true);
            json = write(trace);
        }
        return json;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private int rank(String risk) { return Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL").indexOf(risk); }
    private int value(Integer value) { return value == null ? 0 : value; }
    private void addDistinct(List<String> values, String value) { if (StringUtils.hasText(value) && !values.contains(value)) values.add(value); }
    private String joinWithin(List<String> values, int maxBytes) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            String candidate = result.length() == 0 ? value : result + "\n\n" + value;
            if (candidate.getBytes(StandardCharsets.UTF_8).length > maxBytes) break;
            if (result.length() > 0) result.append("\n\n");
            result.append(value);
        }
        return result.toString();
    }
    private String hash(ReviewCommentDraft comment) {
        try {
            String normalized = comment.getFilePath() + "|" + comment.getLine() + "|"
                + comment.getCategory() + "|" + comment.getMessage();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
