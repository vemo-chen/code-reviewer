package com.vemo.codereview.projecttemplate.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateDetailResponse;
import com.vemo.codereview.projecttemplate.model.ProjectTemplatePageResponse;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateQueryRequest;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateUpsertRequest;
import com.vemo.codereview.projecttemplate.service.ProjectTemplateService;
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
@RequestMapping("/api/project-templates")
public class ProjectTemplateController {

    private final ProjectTemplateService projectTemplateService;

    public ProjectTemplateController(ProjectTemplateService projectTemplateService) {
        this.projectTemplateService = projectTemplateService;
    }

    @GetMapping
    public ApiResponse<ProjectTemplatePageResponse> page(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) String templateName) {
        ProjectTemplateQueryRequest request = new ProjectTemplateQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setTemplateName(templateName);
        return ApiResponse.success(projectTemplateService.page(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectTemplateDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(projectTemplateService.getById(id));
    }

    @PostMapping
    public ApiResponse<ProjectTemplateDetailResponse> create(@RequestBody ProjectTemplateUpsertRequest request) {
        return ApiResponse.success(projectTemplateService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectTemplateDetailResponse> update(
        @PathVariable Long id,
        @RequestBody ProjectTemplateUpsertRequest request) {
        return ApiResponse.success(projectTemplateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        projectTemplateService.delete(id);
        return ApiResponse.success(Boolean.TRUE);
    }
}
