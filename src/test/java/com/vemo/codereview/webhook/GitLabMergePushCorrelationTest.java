package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.model.MergePushDecision;
import com.vemo.codereview.webhook.model.MergePushCorrelationResult;
import com.vemo.codereview.webhook.service.MergePushCorrelationService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitLabMergePushCorrelationTest {
    @Mock private ReviewEventStoreMapper eventMapper;
    @Mock private ReviewTaskStoreMapper taskMapper;

    @Test
    void shouldSkipPushCoveredBySuccessfulMergeRequest() {
        CodeReviewEventEntity event = mrEvent();
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setId(88L);
        task.setTaskType("MR_REVIEW");
        task.setStatus("SUCCESS");
        when(eventMapper.selectList(any())).thenReturn(Arrays.asList(event));
        when(taskMapper.selectOne(any())).thenReturn(task);

        MergePushCorrelationResult result = new MergePushCorrelationService(eventMapper, taskMapper)
            .correlate(push("merge-sha", "c2", "Merge branch 'source-cr'"), project());

        assertEquals(MergePushDecision.SKIP_ALREADY_REVIEWED, result.getDecision());
        assertEquals(Long.valueOf(88L), result.getReviewedMrTaskId());
    }

    @Test
    void shouldCreatePushReviewWhenMergeRequestWasNotSuccessful() {
        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setTaskType("MR_REVIEW");
        task.setStatus("FAILED");
        when(eventMapper.selectList(any())).thenReturn(Arrays.asList(mrEvent()));
        when(taskMapper.selectOne(any())).thenReturn(task);

        MergePushCorrelationResult result = new MergePushCorrelationService(eventMapper, taskMapper)
            .correlate(push("merge-sha", "c2", "Merge branch 'source-cr'"), project());

        assertEquals(MergePushDecision.CREATE_PUSH_REVIEW, result.getDecision());
        assertEquals(null, result.getReviewedMrTaskId());
    }

    private CodeReviewEventEntity mrEvent() {
        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setId(1L);
        event.setProjectId(10L);
        event.setObjectType("merge_request");
        event.setSubmitBranch("test-cr");
        event.setMrHeadSha("c2");
        event.setMergedSha("merge-sha");
        return event;
    }

    private ProjectProfileEntity project() {
        ProjectProfileEntity project = new ProjectProfileEntity();
        project.setId(10L);
        return project;
    }

    private GitLabWebhookPayload push(String after, String head, String title) {
        GitLabWebhookPayload payload = new GitLabWebhookPayload();
        payload.setRef("refs/heads/test-cr");
        payload.setAfter(after);
        GitLabWebhookPayload.Commit commit = new GitLabWebhookPayload.Commit();
        commit.setId(head);
        commit.setTitle("feature");
        GitLabWebhookPayload.Commit merge = new GitLabWebhookPayload.Commit();
        merge.setId(after);
        merge.setTitle(title);
        payload.setCommits(Arrays.asList(commit, merge));
        return payload;
    }
}
