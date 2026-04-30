package com.vemo.codereview.projecttemplate.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.projecttemplate.entity.ProjectTemplateEntity;
import com.vemo.codereview.projecttemplate.mapper.ProjectTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectTemplateResolverService {

    private final ProjectProfileMapper projectProfileMapper;
    private final ProjectTemplateMapper projectTemplateMapper;

    public ProjectTemplateResolverService(
        ProjectProfileMapper projectProfileMapper,
        ProjectTemplateMapper projectTemplateMapper) {
        this.projectProfileMapper = projectProfileMapper;
        this.projectTemplateMapper = projectTemplateMapper;
    }

    public ProjectTemplateEntity resolveProjectTemplate(Long projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectProfileEntity project = projectProfileMapper.selectById(projectId);
        if (project == null || project.getTemplateId() == null) {
            return null;
        }
        ProjectTemplateEntity template = projectTemplateMapper.selectById(project.getTemplateId());
        if (template == null) {
            throw new DomainException("PROJECT_TEMPLATE_NOT_FOUND", "Project template not found");
        }
        return template;
    }

    public String resolveEffectivePrompt(ProjectProfileEntity project, String systemDefaultPrompt) {
        if (project != null && StringUtils.hasText(project.getPromptContent())) {
            return project.getPromptContent().trim();
        }
        ProjectTemplateEntity template = resolveTemplateByProject(project);
        if (template != null && StringUtils.hasText(template.getBaseReviewPrompt())) {
            return template.getBaseReviewPrompt().trim();
        }
        return StringUtils.hasText(systemDefaultPrompt) ? systemDefaultPrompt.trim() : null;
    }

    public String resolveEffectiveFileExtensions(ProjectProfileEntity project) {
        if (project != null && StringUtils.hasText(project.getSupportedFileExtensions())) {
            return project.getSupportedFileExtensions().trim();
        }
        ProjectTemplateEntity template = resolveTemplateByProject(project);
        if (template != null && StringUtils.hasText(template.getFileExtensions())) {
            return template.getFileExtensions().trim();
        }
        return null;
    }

    private ProjectTemplateEntity resolveTemplateByProject(ProjectProfileEntity project) {
        if (project == null || project.getTemplateId() == null) {
            return null;
        }
        ProjectTemplateEntity template = projectTemplateMapper.selectById(project.getTemplateId());
        if (template == null) {
            throw new DomainException("PROJECT_TEMPLATE_NOT_FOUND", "Project template not found");
        }
        return template;
    }
}
