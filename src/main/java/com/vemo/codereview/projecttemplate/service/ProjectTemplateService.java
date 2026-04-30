package com.vemo.codereview.projecttemplate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.projecttemplate.entity.ProjectTemplateEntity;
import com.vemo.codereview.projecttemplate.mapper.ProjectTemplateMapper;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateDetailResponse;
import com.vemo.codereview.projecttemplate.model.ProjectTemplatePageResponse;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateQueryRequest;
import com.vemo.codereview.projecttemplate.model.ProjectTemplateUpsertRequest;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectTemplateService {

    private final ProjectTemplateMapper projectTemplateMapper;
    private final CurrentUserService currentUserService;

    public ProjectTemplateService(
        ProjectTemplateMapper projectTemplateMapper,
        CurrentUserService currentUserService) {
        this.projectTemplateMapper = projectTemplateMapper;
        this.currentUserService = currentUserService;
    }

    public ProjectTemplatePageResponse page(ProjectTemplateQueryRequest request) {
        long pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        long pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        QueryWrapper<ProjectTemplateEntity> wrapper = new QueryWrapper<ProjectTemplateEntity>();
        if (StringUtils.hasText(request.getTemplateName())) {
            wrapper.like("template_name", request.getTemplateName().trim());
        }
        wrapper.orderByDesc("id");

        Page<ProjectTemplateEntity> page = projectTemplateMapper.selectPage(
            new Page<ProjectTemplateEntity>(pageNo, pageSize),
            wrapper
        );

        ProjectTemplatePageResponse response = new ProjectTemplatePageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotal());
        for (ProjectTemplateEntity entity : page.getRecords()) {
            response.getRecords().add(toDetailResponse(entity));
        }
        return response;
    }

    public ProjectTemplateDetailResponse getById(Long id) {
        return toDetailResponse(requireTemplate(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectTemplateDetailResponse create(ProjectTemplateUpsertRequest request) {
        validateRequest(request);

        Date now = new Date();
        Long currentUserId = currentUserService.requireCurrentUserId();
        ProjectTemplateEntity entity = new ProjectTemplateEntity();
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setTemplateDesc(normalizeText(request.getTemplateDesc()));
        entity.setFileExtensions(normalizeText(request.getFileExtensions()));
        entity.setBaseReviewPrompt(normalizeText(request.getBaseReviewPrompt()));
        entity.setCreatedBy(currentUserId);
        entity.setUpdatedBy(currentUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        projectTemplateMapper.insert(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectTemplateDetailResponse update(Long id, ProjectTemplateUpsertRequest request) {
        validateRequest(request);

        ProjectTemplateEntity entity = requireTemplate(id);
        requireManage(entity);
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setTemplateDesc(normalizeText(request.getTemplateDesc()));
        entity.setFileExtensions(normalizeText(request.getFileExtensions()));
        entity.setBaseReviewPrompt(normalizeText(request.getBaseReviewPrompt()));
        entity.setUpdatedBy(currentUserService.requireCurrentUserId());
        entity.setUpdatedAt(new Date());
        projectTemplateMapper.updateById(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProjectTemplateEntity entity = requireTemplate(id);
        requireManage(entity);
        projectTemplateMapper.deleteById(id);
    }

    private ProjectTemplateEntity requireTemplate(Long id) {
        ProjectTemplateEntity entity = projectTemplateMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("PROJECT_TEMPLATE_NOT_FOUND", "Project template not found");
        }
        return entity;
    }

    private void validateRequest(ProjectTemplateUpsertRequest request) {
        if (!StringUtils.hasText(request.getTemplateName())) {
            throw new DomainException("PROJECT_TEMPLATE_NAME_REQUIRED", "Template name is required");
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ProjectTemplateDetailResponse toDetailResponse(ProjectTemplateEntity entity) {
        ProjectTemplateDetailResponse response = new ProjectTemplateDetailResponse();
        response.setId(entity.getId());
        response.setTemplateName(entity.getTemplateName());
        response.setTemplateDesc(entity.getTemplateDesc());
        response.setFileExtensions(entity.getFileExtensions());
        response.setBaseReviewPrompt(entity.getBaseReviewPrompt());
        response.setCreatedBy(entity.getCreatedBy());
        response.setManageable(canManage(entity));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private void requireManage(ProjectTemplateEntity entity) {
        if (!canManage(entity)) {
            throw new DomainException("AUTH_FORBIDDEN", "Current user cannot manage this project template");
        }
    }

    private boolean canManage(ProjectTemplateEntity entity) {
        if (currentUserService.isAdmin()) {
            return true;
        }
        Long currentUserId = currentUserService.requireCurrentUserId();
        return entity.getCreatedBy() != null && entity.getCreatedBy().equals(currentUserId);
    }
}
