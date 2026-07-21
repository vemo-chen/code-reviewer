package com.vemo.codereview.dashboard.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.dashboard.model.BatchRetryRequest;
import com.vemo.codereview.dashboard.model.BatchRetryResponse;
import com.vemo.codereview.dashboard.model.DeveloperStatsResponse;
import com.vemo.codereview.dashboard.model.ProjectOverviewResponse;
import com.vemo.codereview.dashboard.model.ReviewSubmitterOptionResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskDetailResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskPageResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskQueryRequest;
import com.vemo.codereview.dashboard.model.ScoreStatsResponse;
import com.vemo.codereview.dashboard.service.DashboardQueryService;
import com.vemo.codereview.review.service.ReviewTaskManualRetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final ReviewTaskManualRetryService reviewTaskManualRetryService;

    public DashboardController(
        DashboardQueryService dashboardQueryService,
        ReviewTaskManualRetryService reviewTaskManualRetryService) {
        this.dashboardQueryService = dashboardQueryService;
        this.reviewTaskManualRetryService = reviewTaskManualRetryService;
    }

    @GetMapping("/review-tasks")
    public ApiResponse<ReviewTaskPageResponse> reviewTasks(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String fixStatus,
        @RequestParam(required = false) String riskLevel,
        @RequestParam(required = false) String gitlabUsername,
        @RequestParam(required = false) String operatorName,
        @RequestParam(required = false) String targetTitle,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @RequestParam(required = false) String sortField,
        @RequestParam(required = false) String sortOrder) {
        ReviewTaskQueryRequest request = new ReviewTaskQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setProjectId(projectId);
        request.setStatus(status);
        request.setFixStatus(fixStatus);
        request.setRiskLevel(riskLevel);
        request.setGitlabUsername(gitlabUsername);
        request.setOperatorName(operatorName);
        request.setTargetTitle(targetTitle);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setSortField(sortField);
        request.setSortOrder(sortOrder);
        return ApiResponse.success(dashboardQueryService.pageReviewTasks(request));
    }

    @GetMapping("/review-tasks/{taskId}")
    public ApiResponse<ReviewTaskDetailResponse> reviewTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.success(dashboardQueryService.getReviewTaskDetail(taskId));
    }


    @GetMapping("/review-submitters")
    public ApiResponse<ReviewSubmitterOptionResponse> reviewSubmitters() {
        return ApiResponse.success(dashboardQueryService.getReviewSubmitters());
    }

    @PostMapping("/review-tasks/{taskId}/retry")
    public ApiResponse<Void> retryReviewTask(@PathVariable Long taskId) {
        reviewTaskManualRetryService.retry(taskId);
        return ApiResponse.success(null);
    }

    @PostMapping("/review-tasks/{taskId}/interrupt")
    public ApiResponse<Void> interruptReviewTask(@PathVariable Long taskId) {
        reviewTaskManualRetryService.interrupt(taskId);
        return ApiResponse.success(null);
    }

    @PostMapping("/review-tasks/batch-retry")
    public ApiResponse<BatchRetryResponse> batchRetryReviewTask(@RequestBody BatchRetryRequest request) {
        return ApiResponse.success(reviewTaskManualRetryService.batchRetry(request.getTaskIds()));
    }

    @GetMapping("/project-stats")
    public ApiResponse<ProjectOverviewResponse> projectStats() {
        return ApiResponse.success(dashboardQueryService.getProjectStats());
    }

    @GetMapping("/developer-stats")
    public ApiResponse<DeveloperStatsResponse> developerStats() {
        return ApiResponse.success(dashboardQueryService.getDeveloperStats());
    }

    @GetMapping("/score-stats")
    public ApiResponse<ScoreStatsResponse> scoreStats(
        @RequestParam String startDate,
        @RequestParam String endDate) {
        return ApiResponse.success(dashboardQueryService.getScoreStats(startDate, endDate));
    }
}
