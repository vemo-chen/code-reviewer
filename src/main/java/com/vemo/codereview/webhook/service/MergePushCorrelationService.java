package com.vemo.codereview.webhook.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.model.MergePushDecision;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MergePushCorrelationService {
    private final ReviewEventStoreMapper eventMapper;
    private final ReviewTaskStoreMapper taskMapper;

    public MergePushCorrelationService(ReviewEventStoreMapper eventMapper, ReviewTaskStoreMapper taskMapper) {
        this.eventMapper = eventMapper;
        this.taskMapper = taskMapper;
    }

    public MergePushDecision decide(GitLabWebhookPayload push, ProjectProfileEntity project) {
        String branch = branch(push.getRef());
        List<CodeReviewEventEntity> events = eventMapper.selectList(new QueryWrapper<CodeReviewEventEntity>()
            .eq("project_id", project.getId()).eq("object_type", "merge_request").eq("submit_branch", branch)
            .orderByDesc("updated_at"));
        boolean correlatedToUnsuccessful = false;
        if (events != null) for (CodeReviewEventEntity event : events) {
            if (!hasEvidence(push, event)) continue;
            CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>()
                .eq("event_id", event.getId()).last("limit 1"));
            if (task != null && "MR_REVIEW".equals(task.getTaskType()) && "SUCCESS".equals(task.getStatus())
                && !"IGNORED".equals(event.getStatus()) && !"FAILED".equals(event.getStatus())) {
                return MergePushDecision.SKIP_ALREADY_REVIEWED;
            }
            correlatedToUnsuccessful = true;
        }
        if (correlatedToUnsuccessful) return MergePushDecision.CREATE_PUSH_REVIEW;
        return containsOnlyMergeCommits(push) ? MergePushDecision.IGNORE_NO_CODE
            : MergePushDecision.CREATE_PUSH_REVIEW;
    }

    private boolean hasEvidence(GitLabWebhookPayload push, CodeReviewEventEntity event) {
        if (equals(push.getAfter(), event.getMergedSha()) || equals(push.getAfter(), event.getMrHeadSha())) return true;
        if (!afterIsMerge(push)) return false;
        if (push.getCommits() != null) for (GitLabWebhookPayload.Commit commit : push.getCommits()) {
            if (commit != null && equals(commit.getId(), event.getMrHeadSha())) return true;
        }
        return false;
    }

    private boolean afterIsMerge(GitLabWebhookPayload push) {
        if (push.getCommits() == null) return false;
        for (GitLabWebhookPayload.Commit commit : push.getCommits()) {
            if (commit == null || !equals(commit.getId(), push.getAfter())) continue;
            String text = StringUtils.hasText(commit.getTitle()) ? commit.getTitle() : commit.getMessage();
            return StringUtils.hasText(text) && text.trim().startsWith("Merge ");
        }
        return false;
    }

    private boolean containsOnlyMergeCommits(GitLabWebhookPayload push) {
        if (push.getCommits() == null || push.getCommits().isEmpty()) return false;
        for (GitLabWebhookPayload.Commit commit : push.getCommits()) {
            String text = commit == null ? null : (StringUtils.hasText(commit.getTitle()) ? commit.getTitle() : commit.getMessage());
            if (!StringUtils.hasText(text) || !text.trim().startsWith("Merge ")) return false;
        }
        return true;
    }

    private String branch(String ref) { return ref != null && ref.startsWith("refs/heads/") ? ref.substring(11) : ref; }
    private boolean equals(String left, String right) { return StringUtils.hasText(left) && left.equals(right); }
}
