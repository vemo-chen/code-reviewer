package com.vemo.codereview.webhook.controller;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.service.GitLabWebhookHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/gitlab")
public class GitLabWebhookController {

    private final GitLabWebhookHandlerService gitLabWebhookHandlerService;

    public GitLabWebhookController(GitLabWebhookHandlerService gitLabWebhookHandlerService) {
        this.gitLabWebhookHandlerService = gitLabWebhookHandlerService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> handleWebhook(
        @RequestHeader("X-Gitlab-Token") String token,
        @RequestBody GitLabWebhookPayload payload) {
        long startNs = System.nanoTime();
        try {
            log.info("received webhook event: {}", payload.getObjectKind());
            gitLabWebhookHandlerService.handleWebhook(token, payload);
            log.info("webhook request accepted. eventType={}, elapsedMs={}", payload.getObjectKind(), elapsedMs(startNs));
            return ResponseEntity.ok(ApiResponse.success("accepted"));
        } catch (DomainException ex) {
            log.warn("webhook request rejected. eventType={}, code={}, elapsedMs={}",
                payload == null ? null : payload.getObjectKind(), ex.getCode(), elapsedMs(startNs));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
        }
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
