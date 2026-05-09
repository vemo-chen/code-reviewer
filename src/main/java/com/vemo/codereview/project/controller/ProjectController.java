package com.vemo.codereview.project.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.llm.model.ProjectLlmModelOptionResponse;
import com.vemo.codereview.project.model.GitLabBranchOptionResponse;
import com.vemo.codereview.project.model.GitLabProjectTestRequest;
import com.vemo.codereview.project.model.GitLabProjectTestResponse;
import com.vemo.codereview.project.model.ProjectCustomReviewBatchRequest;
import com.vemo.codereview.project.model.ProjectCustomReviewBatchResponse;
import com.vemo.codereview.project.model.ProjectDetailResponse;
import com.vemo.codereview.project.model.ProjectPageResponse;
import com.vemo.codereview.project.model.ProjectQueryRequest;
import com.vemo.codereview.project.model.ProjectUpsertRequest;
import com.vemo.codereview.project.service.ProjectGitLabTestService;
import com.vemo.codereview.project.service.ProjectGitLabBranchService;
import com.vemo.codereview.project.service.ProjectService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectGitLabTestService projectGitLabTestService;
    private final ProjectGitLabBranchService projectGitLabBranchService;

    public ProjectController(ProjectService projectService, ProjectGitLabTestService projectGitLabTestService,
                             ProjectGitLabBranchService projectGitLabBranchService) {
        this.projectService = projectService;
        this.projectGitLabTestService = projectGitLabTestService;
        this.projectGitLabBranchService = projectGitLabBranchService;
    }

    @PostMapping
    public ApiResponse<ProjectDetailResponse> create(@RequestBody ProjectUpsertRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> update(
        @PathVariable Long id,
        @RequestBody ProjectUpsertRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(projectService.getById(id));
    }

    @GetMapping
    public ApiResponse<ProjectPageResponse> list(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) String projectName,
        @RequestParam(required = false) String gitlabProjectUrl,
        @RequestParam(required = false) Boolean aiReviewEnabled,
        @RequestParam(required = false) Boolean wecomNotifyEnabled) {
        ProjectQueryRequest request = new ProjectQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setProjectName(projectName);
        request.setGitlabProjectUrl(gitlabProjectUrl);
        request.setAiReviewEnabled(aiReviewEnabled);
        request.setWecomNotifyEnabled(wecomNotifyEnabled);
        return ApiResponse.success(projectService.page(request));
    }

    @GetMapping("/{id}/llm-models")
    public ApiResponse<List<ProjectLlmModelOptionResponse>> listProjectLlmModels(@PathVariable Long id) {
        return ApiResponse.success(projectService.listAvailableLlmModels(id));
    }

    @PostMapping("/gitlab/test")
    public ApiResponse<GitLabProjectTestResponse> testGitLab(@RequestBody GitLabProjectTestRequest request) {
        return ApiResponse.success(projectGitLabTestService.test(request));
    }

    @PostMapping("/gitlab/branches")
    public ApiResponse<List<GitLabBranchOptionResponse>> listGitLabBranches(@RequestBody GitLabProjectTestRequest request) {
        return ApiResponse.success(projectGitLabBranchService.listBranches(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success("deleted");
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<ProjectDetailResponse> refresh(@PathVariable Long id) {
        return ApiResponse.success(projectService.refresh(id));
    }

    @PostMapping("/{id}/custom-review-batches")
    public ApiResponse<ProjectCustomReviewBatchResponse> createCustomReviewBatch(
        @PathVariable Long id,
        @RequestBody ProjectCustomReviewBatchRequest request) {
        return ApiResponse.success(projectService.createCustomReviewBatch(id, request));
    }
}
