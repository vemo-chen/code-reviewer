package com.vemo.codereview.notify.client;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.notify.model.WeComMarkdownPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WeComWebhookClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 300L;

    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient;

    @Autowired
    public WeComWebhookClient(ObjectMapper objectMapper) {
        this(objectMapper, buildClient());
    }

    public WeComWebhookClient(
        ObjectMapper objectMapper,
        OkHttpClient okHttpClient) {
        this.objectMapper = objectMapper;
        this.okHttpClient = okHttpClient;
    }

    public void sendMarkdown(String webhookUrl, WeComMarkdownPayload message) {
        if (!StringUtils.hasText(webhookUrl)) {
            throw new DomainException("WECOM_WEBHOOK_MISSING", "WeCom webhook url is not configured");
        }

        String url = webhookUrl.trim();
        try {
            String body = objectMapper.writeValueAsString(message);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                log.info("WeCom push started. attempt={}, webhook={}", attempt, maskWebhook(url));
                Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();

                try {
                    try (Response response = okHttpClient.newCall(request).execute()) {
                        String responseBody = response.body() == null ? "" : response.body().string();
                        JsonNode responseJson = responseBody.isEmpty() ? null : objectMapper.readTree(responseBody);
                        Integer errcode = responseJson == null || !responseJson.has("errcode")
                            ? null : responseJson.get("errcode").asInt();
                        String errmsg = responseJson == null || !responseJson.has("errmsg")
                            ? "" : responseJson.get("errmsg").asText();
                        boolean success = response.isSuccessful() && (errcode == null || errcode == 0);
                        if (success) {
                            log.info("WeCom push succeeded. attempt={}, httpStatus={}, errcode={}, errmsg={}",
                                attempt, response.code(), errcode, errmsg);
                            return;
                        }
                        log.warn("WeCom push failed. attempt={}, httpStatus={}, errcode={}, errmsg={}",
                            attempt, response.code(), errcode, errmsg);
                        if (attempt == MAX_ATTEMPTS) {
                            throw new DomainException("WECOM_PUSH_ERROR",
                                "WeCom push failed with status=" + response.code() + ", errcode=" + errcode
                                    + ", errmsg=" + errmsg);
                        }
                    }
                } catch (IOException ex) {
                    log.warn("WeCom push I/O or response parse failure. attempt={}, webhook={}, message={}",
                        attempt, maskWebhook(url), ex.getMessage());
                    if (attempt == MAX_ATTEMPTS) {
                        throw new DomainException("WECOM_PUSH_IO_ERROR", "Failed to push WeCom notification");
                    }
                }
                sleepBeforeRetry(attempt);
            }
        } catch (IOException ex) {
            log.warn("WeCom push request serialization failed. webhook={}, message={}", maskWebhook(url), ex.getMessage());
            throw new DomainException("WECOM_PUSH_IO_ERROR", "Failed to serialize WeCom notification");
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DomainException("WECOM_PUSH_INTERRUPTED", "WeCom push retry interrupted");
        }
    }

    private String maskWebhook(String webhookUrl) {
        int queryStart = webhookUrl.indexOf('?');
        if (queryStart < 0) {
            return webhookUrl;
        }
        return webhookUrl.substring(0, queryStart) + "?...";
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(5000L, TimeUnit.MILLISECONDS)
            .readTimeout(10000L, TimeUnit.MILLISECONDS)
            .writeTimeout(10000L, TimeUnit.MILLISECONDS)
            .build();
    }
}
