package com.vemo.codereview.review.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.review.entity.CodeReviewFixFlowEntity;
import com.vemo.codereview.review.model.ReviewFixActionRequest;
import com.vemo.codereview.review.model.ReviewFixFlowResponse;
import com.vemo.codereview.review.service.ReviewFixWorkflowService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-tasks")
public class ReviewFixWorkflowController {

    private final ReviewFixWorkflowService reviewFixWorkflowService;

    public ReviewFixWorkflowController(ReviewFixWorkflowService reviewFixWorkflowService) {
        this.reviewFixWorkflowService = reviewFixWorkflowService;
    }

    @PostMapping("/{taskId}/submit-fix-review")
    public ApiResponse<Void> submitFixReview(
        @PathVariable Long taskId,
        @RequestBody(required = false) ReviewFixActionRequest request) {
        reviewFixWorkflowService.submitForReview(taskId, request == null ? null : request.getComment());
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/approve-fix")
    public ApiResponse<Void> approveFix(
        @PathVariable Long taskId,
        @RequestBody(required = false) ReviewFixActionRequest request) {
        reviewFixWorkflowService.approveFix(taskId, request == null ? null : request.getComment());
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/reject-fix")
    public ApiResponse<Void> rejectFix(
        @PathVariable Long taskId,
        @RequestBody ReviewFixActionRequest request) {
        reviewFixWorkflowService.rejectFix(taskId, request == null ? null : request.getComment());
        return ApiResponse.success(null);
    }

    @GetMapping("/{taskId}/fix-flows")
    public ApiResponse<ReviewFixFlowResponse> fixFlows(@PathVariable Long taskId) {
        List<CodeReviewFixFlowEntity> flows = reviewFixWorkflowService.listFlows(taskId);
        ReviewFixFlowResponse response = new ReviewFixFlowResponse();
        List<ReviewFixFlowResponse.Item> records = new ArrayList<ReviewFixFlowResponse.Item>();
        for (CodeReviewFixFlowEntity flow : flows) {
            ReviewFixFlowResponse.Item item = new ReviewFixFlowResponse.Item();
            item.setId(flow.getId());
            item.setTaskId(flow.getTaskId());
            item.setFromStatus(flow.getFromStatus());
            item.setToStatus(flow.getToStatus());
            item.setOperatorUserId(flow.getOperatorUserId());
            item.setOperatorName(flow.getOperatorName());
            item.setComment(flow.getComment());
            item.setCreatedAt(flow.getCreatedAt());
            records.add(item);
        }
        response.setRecords(records);
        return ApiResponse.success(response);
    }
}
