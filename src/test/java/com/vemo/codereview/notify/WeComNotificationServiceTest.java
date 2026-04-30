package com.vemo.codereview.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.notify.client.WeComWebhookClient;
import com.vemo.codereview.notify.model.ReviewNotificationMetadata;
import com.vemo.codereview.notify.service.WeComNotificationService;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeComNotificationServiceTest {

    private MockWebServer mockWebServer;
    private WeComNotificationService weComNotificationService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WeComWebhookClient webhookClient = new WeComWebhookClient(new ObjectMapper(), new OkHttpClient());
        weComNotificationService = new WeComNotificationService(webhookClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldSendMarkdownMessageToWeComWebhook() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"errcode\":0,\"errmsg\":\"ok\"}"));

        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setRiskLevel("HIGH");
        result.setSuggestedScore(88);
        result.setDeductionScore(6);
        result.setFinalScore(82);
        result.setScoreReason("Suggested score: 88; Deducted 6 points; Final score: 82");
        result.setSummary("Found one high risk issue. Fix before merge.");
        result.setBriefSummary("Found one high risk issue");
        result.setAdvice("Fix and verify before merge");

        CodeReviewCommentEntity comment = new CodeReviewCommentEntity();
        comment.setFilePath("src/main/java/com/vemo/App.java");
        comment.setLineNo(42);
        comment.setSeverity("HIGH");
        comment.setCategory("Project hard rule");
        comment.setMessage("A project convention was violated");
        comment.setSuggestion("Use Getter and Setter annotations");

        ReviewNotificationMetadata metadata = new ReviewNotificationMetadata();
        metadata.setReviewTargetType("PUSH_REVIEW");
        metadata.setTargetId("abcdef123456");
        metadata.setSubmitMessage("fix: improve review worker");
        metadata.setSubmitter("alice");
        metadata.setSubmitBranch("feature/review-worker");
        metadata.setSubmitTime("2026-04-07 18:00:00");

        boolean notified = weComNotificationService.notifyReviewResult(
            1001L,
            metadata,
            result,
            Arrays.asList(comment),
            mockWebServer.url("/cgi-bin/webhook/send?key=test-key").toString()
        );

        RecordedRequest request = mockWebServer.takeRequest();
        JsonNode payload = new ObjectMapper().readTree(request.getBody().readUtf8());
        String markdown = payload.get("markdown").get("content").asText();

        assertTrue(notified);
        assertEquals("/cgi-bin/webhook/send?key=test-key", request.getPath());
        assertEquals("markdown", payload.get("msgtype").asText());
        assertTrue(markdown.contains("Project ID"));
        assertTrue(markdown.contains("Commit SHA"));
        assertTrue(markdown.contains("Submit message"));
        assertTrue(markdown.contains("Submitter"));
        assertTrue(markdown.contains("Branch"));
        assertTrue(markdown.contains("Submitted at"));
        assertTrue(markdown.contains("Score"));
        assertTrue(markdown.contains("Score reason"));
        assertTrue(markdown.contains("Advice"));
        assertFalse(markdown.contains("Rule deduction"));
        assertTrue(markdown.contains("Suggested score: 88; Deducted 6 points; Final score: 82"));
        assertTrue(markdown.contains("Found one high risk issue"));
        assertTrue(markdown.contains("> Risk: `HIGH`"));
        assertTrue(markdown.contains("**1. Project hard rule[`HIGH`]**"));
        assertTrue(markdown.contains("**Location:** src/main/java/com/vemo/App.java:42"));
        assertTrue(markdown.contains("**Issue:** A project convention was violated"));
        assertTrue(markdown.contains("**Suggestion:** Use Getter and Setter annotations"));
        assertTrue(markdown.contains("**Full summary**"));
    }

    @Test
    void shouldUseProjectWebhookOverrideWhenProvided() throws Exception {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"errcode\":0,\"errmsg\":\"ok\"}"));

        String overrideWebhook = mockWebServer.url("/cgi-bin/webhook/send?key=project-key").toString();
        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setRiskLevel("LOW");
        result.setSummary("summary");

        boolean notified = weComNotificationService.notifyReviewResult(
            1001L,
            new ReviewNotificationMetadata(),
            result,
            Arrays.<CodeReviewCommentEntity>asList(),
            overrideWebhook
        );

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(notified);
        assertEquals("/cgi-bin/webhook/send?key=project-key", request.getPath());
    }
}
