package com.vemo.codereview.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.notify.model.ReviewNotificationMetadata;
import com.vemo.codereview.notify.service.WeComNotificationService;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

@SpringBootTest
@ActiveProfiles("local")
class WeComNotificationManualIT {

    @Autowired
    private WeComNotificationService weComNotificationService;

    @Autowired
    private ReviewResultStoreMapper reviewResultStoreMapper;

    @Autowired
    private ReviewCommentStoreMapper reviewCommentStoreMapper;

    @Autowired
    private ReviewTaskStoreMapper reviewTaskStoreMapper;

    @Autowired
    private ReviewEventStoreMapper reviewEventStoreMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPreviewAndSendWeComMessageForResultOne() throws Exception {
        CodeReviewResultEntity result = reviewResultStoreMapper.selectById(3L);
        assertNotNull(result, "code_review_result.id=3 does not exist; prepare local test data first");

        QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
        commentWrapper.eq("result_id", 3L).orderByAsc("id");
        List<CodeReviewCommentEntity> comments = reviewCommentStoreMapper.selectList(commentWrapper);
        assertFalse(comments.isEmpty(), "No comments found for result_id=3");

        CodeReviewTaskEntity task = reviewTaskStoreMapper.selectById(result.getTaskId());
        assertNotNull(task, "Related code_review_task does not exist");

        CodeReviewEventEntity event = reviewEventStoreMapper.selectById(task.getEventId());
        ReviewNotificationMetadata metadata = buildMetadata(task, event);

        String markdown = weComNotificationService.previewReviewMarkdown(task.getProjectId(), metadata, result, comments);

        System.out.println("================ WeCom Markdown Preview ================");
        System.out.println(markdown);
        System.out.println("========================================================");

        assertTrue(markdown.contains("AI Code Review"));
        assertTrue(markdown.contains("Risk"));
        assertTrue(markdown.contains("Submitter"));
        assertTrue(markdown.contains(comments.get(0).getFilePath()));

        boolean notified = weComNotificationService.notifyReviewResult(task.getProjectId(), metadata, result, comments);

        assertTrue(notified, "WeCom message was not sent; check local webhook configuration");
    }

    private ReviewNotificationMetadata buildMetadata(CodeReviewTaskEntity task, CodeReviewEventEntity event) throws Exception {
        ReviewNotificationMetadata metadata = new ReviewNotificationMetadata();
        metadata.setReviewTargetType(task.getTaskType());
        metadata.setTargetId(task.getTargetId());
        metadata.setSubmitMessage(task.getTargetTitle());
        if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
            return metadata;
        }
        GitLabWebhookPayload payload = objectMapper.readValue(event.getPayloadJson(), GitLabWebhookPayload.class);
        if (payload.getUser() != null && StringUtils.hasText(payload.getUser().getName())) {
            metadata.setSubmitter(payload.getUser().getName());
        } else {
            metadata.setSubmitter(event.getOperatorName());
        }
        if (payload.getObjectAttributes() != null && StringUtils.hasText(payload.getObjectAttributes().getSourceBranch())) {
            metadata.setSubmitBranch(payload.getObjectAttributes().getSourceBranch());
        } else if (StringUtils.hasText(payload.getRef())) {
            String ref = payload.getRef();
            int index = ref.lastIndexOf('/');
            metadata.setSubmitBranch(index >= 0 ? ref.substring(index + 1) : ref);
        }
        if (payload.getCommits() != null && !payload.getCommits().isEmpty()) {
            GitLabWebhookPayload.Commit latest = payload.getCommits().get(payload.getCommits().size() - 1);
            if (latest != null) {
                if (StringUtils.hasText(latest.getTitle())) {
                    metadata.setSubmitMessage(latest.getTitle().trim());
                } else if (StringUtils.hasText(latest.getMessage())) {
                    metadata.setSubmitMessage(latest.getMessage().trim());
                }
                metadata.setSubmitTime(formatTimestamp(latest.getTimestamp(), task.getCreatedAt()));
            }
        }
        return metadata;
    }

    private String formatTimestamp(String timestamp, Date fallback) {
        if (!StringUtils.hasText(timestamp)) {
            return fallback == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(fallback);
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            Date parsed = parser.parse(timestamp);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return formatter.format(parsed);
        } catch (ParseException ex) {
            return timestamp;
        }
    }
}
