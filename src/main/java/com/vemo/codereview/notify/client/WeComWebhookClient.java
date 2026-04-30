package com.vemo.codereview.notify.client;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.notify.model.WeComMarkdownPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class WeComWebhookClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

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

        try {
            Request request = new Request.Builder()
                .url(webhookUrl.trim())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(message), JSON))
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new DomainException("WECOM_PUSH_ERROR", "WeCom push failed with status " + response.code());
                }
            }
        } catch (IOException ex) {
            throw new DomainException("WECOM_PUSH_IO_ERROR", "Failed to push WeCom notification");
        }
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(5000L, TimeUnit.MILLISECONDS)
            .readTimeout(10000L, TimeUnit.MILLISECONDS)
            .writeTimeout(10000L, TimeUnit.MILLISECONDS)
            .build();
    }
}
