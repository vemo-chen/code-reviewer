package com.vemo.codereview.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewRefResolver {

    private final ReviewEventStoreMapper reviewEventStoreMapper;
    private final ObjectMapper objectMapper;

    public ReviewRefResolver(ReviewEventStoreMapper reviewEventStoreMapper, ObjectMapper objectMapper) {
        this.reviewEventStoreMapper = reviewEventStoreMapper;
        this.objectMapper = objectMapper;
    }

    public void resolve(ReviewExecutionContext context) {
        if (context == null) {
            return;
        }
        if ("commit".equals(context.getTargetType())) {
            context.setSourceRef(context.getTargetId());
            context.setHeadSha(context.getTargetId());
            return;
        }
        GitLabWebhookPayload payload = readPayload(context.getEventId());
        if (payload == null || payload.getObjectAttributes() == null) {
            context.setSourceRef(context.getTargetId());
            return;
        }
        if (payload.getObjectAttributes().getLastCommit() != null
            && StringUtils.hasText(payload.getObjectAttributes().getLastCommit().getId())) {
            context.setHeadSha(payload.getObjectAttributes().getLastCommit().getId());
            context.setSourceRef(payload.getObjectAttributes().getLastCommit().getId());
        } else {
            context.setSourceRef(payload.getObjectAttributes().getSourceBranch());
        }
        context.setTargetRef(payload.getObjectAttributes().getTargetBranch());
    }

    private GitLabWebhookPayload readPayload(Long eventId) {
        if (eventId == null) {
            return null;
        }
        CodeReviewEventEntity event = reviewEventStoreMapper.selectById(eventId);
        if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(event.getPayloadJson(), GitLabWebhookPayload.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
