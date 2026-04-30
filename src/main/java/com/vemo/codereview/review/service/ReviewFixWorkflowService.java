package com.vemo.codereview.review.service;

import com.vemo.codereview.auth.model.AuthSession;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.project.service.ProjectPermissionService;
import com.vemo.codereview.review.entity.CodeReviewFixFlowEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewFixFlowMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewFixStatus;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewFixWorkflowService {

    private final ReviewTaskStoreMapper reviewTaskStoreMapper;
    private final ReviewFixFlowMapper reviewFixFlowMapper;
    private final CurrentUserService currentUserService;
    private final ProjectPermissionService projectPermissionService;

    public ReviewFixWorkflowService(
        ReviewTaskStoreMapper reviewTaskStoreMapper,
        ReviewFixFlowMapper reviewFixFlowMapper,
        CurrentUserService currentUserService,
        ProjectPermissionService projectPermissionService) {
        this.reviewTaskStoreMapper = reviewTaskStoreMapper;
        this.reviewFixFlowMapper = reviewFixFlowMapper;
        this.currentUserService = currentUserService;
        this.projectPermissionService = projectPermissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long taskId, String comment) {
        CodeReviewTaskEntity task = loadTask(taskId);
        projectPermissionService.requireProjectMember(task.getProjectId());
        requireTaskReviewable(task);
        ReviewFixStatus fromStatus = resolveFixStatus(task.getFixStatus());
        if (!Arrays.asList(ReviewFixStatus.TO_BE_FIXED, ReviewFixStatus.REVIEW_REJECTED).contains(fromStatus)) {
            throw new DomainException("FIX_STATUS_INVALID", "Current task fix status cannot be submitted for review");
        }

        AuthSession currentUser = currentUserService.requireCurrentUser();
        Date now = new Date();
        task.setFixStatus(ReviewFixStatus.TO_BE_REVIEWED.name());
        task.setFixSubmittedBy(currentUser.getUserId());
        task.setFixSubmittedAt(now);
        task.setFixReviewedBy(null);
        task.setFixReviewedAt(null);
        task.setFixReviewComment(null);
        task.setUpdatedAt(now);
        reviewTaskStoreMapper.updateById(task);

        insertFlow(task.getId(), fromStatus, ReviewFixStatus.TO_BE_REVIEWED, currentUser, comment, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveFix(Long taskId, String comment) {
        CodeReviewTaskEntity task = loadTask(taskId);
        projectPermissionService.requireProjectOwner(task.getProjectId());
        requireTaskReviewable(task);
        ReviewFixStatus fromStatus = resolveFixStatus(task.getFixStatus());
        if (fromStatus != ReviewFixStatus.TO_BE_REVIEWED) {
            throw new DomainException("FIX_STATUS_INVALID", "Current task fix status cannot be approved");
        }

        AuthSession currentUser = currentUserService.requireCurrentUser();
        Date now = new Date();
        task.setFixStatus(ReviewFixStatus.REVIEW_PASSED.name());
        task.setFixReviewedBy(currentUser.getUserId());
        task.setFixReviewedAt(now);
        task.setFixReviewComment(normalizeComment(comment));
        task.setUpdatedAt(now);
        reviewTaskStoreMapper.updateById(task);

        insertFlow(task.getId(), fromStatus, ReviewFixStatus.REVIEW_PASSED, currentUser, comment, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectFix(Long taskId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new DomainException("FIX_REVIEW_COMMENT_REQUIRED", "Review comment is required when rejecting fix");
        }
        CodeReviewTaskEntity task = loadTask(taskId);
        projectPermissionService.requireProjectOwner(task.getProjectId());
        requireTaskReviewable(task);
        ReviewFixStatus fromStatus = resolveFixStatus(task.getFixStatus());
        if (fromStatus != ReviewFixStatus.TO_BE_REVIEWED) {
            throw new DomainException("FIX_STATUS_INVALID", "Current task fix status cannot be rejected");
        }

        AuthSession currentUser = currentUserService.requireCurrentUser();
        Date now = new Date();
        task.setFixStatus(ReviewFixStatus.REVIEW_REJECTED.name());
        task.setFixReviewedBy(currentUser.getUserId());
        task.setFixReviewedAt(now);
        task.setFixReviewComment(comment.trim());
        task.setUpdatedAt(now);
        reviewTaskStoreMapper.updateById(task);

        insertFlow(task.getId(), fromStatus, ReviewFixStatus.REVIEW_REJECTED, currentUser, comment, now);
    }

    public List<CodeReviewFixFlowEntity> listFlows(Long taskId) {
        CodeReviewTaskEntity task = loadTask(taskId);
        projectPermissionService.requireProjectAccess(task.getProjectId());
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CodeReviewFixFlowEntity> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CodeReviewFixFlowEntity>();
        wrapper.eq("task_id", taskId).orderByAsc("id");
        return reviewFixFlowMapper.selectList(wrapper);
    }

    private CodeReviewTaskEntity loadTask(Long taskId) {
        CodeReviewTaskEntity task = reviewTaskStoreMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        return task;
    }

    private void requireTaskReviewable(CodeReviewTaskEntity task) {
        if (!ReviewTaskLifecycle.SUCCESS.name().equals(task.getStatus())) {
            throw new DomainException("TASK_REVIEW_NOT_READY", "Only successful review tasks can enter fix workflow");
        }
        if (!StringUtils.hasText(task.getFixStatus())) {
            throw new DomainException("FIX_STATUS_MISSING", "Task fix status is not initialized");
        }
    }

    private ReviewFixStatus resolveFixStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new DomainException("FIX_STATUS_MISSING", "Task fix status is empty");
        }
        return ReviewFixStatus.valueOf(status);
    }

    private void insertFlow(
        Long taskId,
        ReviewFixStatus fromStatus,
        ReviewFixStatus toStatus,
        AuthSession currentUser,
        String comment,
        Date now) {
        CodeReviewFixFlowEntity flow = new CodeReviewFixFlowEntity();
        flow.setTaskId(taskId);
        flow.setFromStatus(fromStatus == null ? null : fromStatus.name());
        flow.setToStatus(toStatus.name());
        flow.setOperatorUserId(currentUser.getUserId());
        flow.setOperatorName(resolveOperatorName(currentUser));
        flow.setComment(normalizeComment(comment));
        flow.setCreatedAt(now);
        reviewFixFlowMapper.insert(flow);
    }

    private String resolveOperatorName(AuthSession currentUser) {
        if (StringUtils.hasText(currentUser.getDisplayName())) {
            return currentUser.getDisplayName().trim();
        }
        if (StringUtils.hasText(currentUser.getUsername())) {
            return currentUser.getUsername().trim();
        }
        return String.valueOf(currentUser.getUserId());
    }

    private String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        return comment.trim();
    }
}
