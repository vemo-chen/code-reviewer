package com.vemo.codereview.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.llm.model.ProjectLlmModelOptionResponse;
import com.vemo.codereview.llm.service.LlmModelService;
import com.vemo.codereview.platform.gitlab.model.GitLabProjectPayload;
import com.vemo.codereview.project.model.ProjectDetailResponse;
import com.vemo.codereview.project.model.ProjectPageResponse;
import com.vemo.codereview.project.model.ProjectQueryRequest;
import com.vemo.codereview.project.model.ProjectUpsertRequest;
import com.vemo.codereview.projecttemplate.entity.ProjectTemplateEntity;
import com.vemo.codereview.projecttemplate.mapper.ProjectTemplateMapper;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.entity.UserProjectRelEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import com.vemo.codereview.user.mapper.UserProjectRelMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class ProjectService {

    private final ProjectProfileMapper projectProfileMapper;
    private final GitLabProjectResolver gitLabProjectResolver;
    private final CurrentUserService currentUserService;
    private final ProjectPermissionService projectPermissionService;
    private final UserMapper userMapper;
    private final UserProjectRelMapper userProjectRelMapper;
    private final LlmModelService llmModelService;
    private final ProjectTemplateMapper projectTemplateMapper;

    public ProjectService(
        ProjectProfileMapper projectProfileMapper,
        GitLabProjectResolver gitLabProjectResolver,
        CurrentUserService currentUserService,
        ProjectPermissionService projectPermissionService,
        UserMapper userMapper,
        UserProjectRelMapper userProjectRelMapper,
        LlmModelService llmModelService,
        ProjectTemplateMapper projectTemplateMapper) {
        this.projectProfileMapper = projectProfileMapper;
        this.gitLabProjectResolver = gitLabProjectResolver;
        this.currentUserService = currentUserService;
        this.projectPermissionService = projectPermissionService;
        this.userMapper = userMapper;
        this.userProjectRelMapper = userProjectRelMapper;
        this.llmModelService = llmModelService;
        this.projectTemplateMapper = projectTemplateMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetailResponse create(ProjectUpsertRequest request) {
        validateRequest(request);
        Long currentUserId = currentUserService.requireCurrentUserId();
        boolean admin = currentUserService.isAdmin();

        Long ownerUserId = resolveCreateOwnerUserId(request, currentUserId, admin);
        List<Long> memberUserIds = normalizeMemberUserIds(request.getMemberUserIds(), ownerUserId, admin ? null : currentUserId);
        validateProjectParticipants(ownerUserId, memberUserIds);

        Date now = new Date();
        ProjectProfileEntity entity = new ProjectProfileEntity();
        entity.setProjectKey(buildProjectKey());
        applyRequest(entity, request);
        entity.setOwnerUserId(ownerUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        projectProfileMapper.insert(entity);
        syncProjectMembers(entity.getId(), memberUserIds);
        llmModelService.requireProjectModelUsable(entity.getId(), request.getLlmModelId());
        entity.setLlmModelId(request.getLlmModelId());
        projectProfileMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetailResponse update(Long id, ProjectUpsertRequest request) {
        validateRequest(request);
        ProjectProfileEntity entity = projectProfileMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        requireProjectManager(entity);

        Long ownerUserId = resolveUpdateOwnerUserId(request);
        List<Long> memberUserIds = normalizeMemberUserIds(request.getMemberUserIds(), ownerUserId, null);
        validateProjectParticipants(ownerUserId, memberUserIds);
        llmModelService.requireProjectModelUsable(id, request.getLlmModelId());

        applyRequest(entity, request);
        entity.setOwnerUserId(ownerUserId);
        entity.setUpdatedAt(new Date());
        projectProfileMapper.updateById(entity);
        syncProjectMembers(entity.getId(), memberUserIds);
        llmModelService.requireProjectModelUsable(entity.getId(), request.getLlmModelId());
        entity.setLlmModelId(request.getLlmModelId());
        projectProfileMapper.updateById(entity);
        return toResponse(entity);
    }

    public ProjectDetailResponse getById(Long id) {
        ProjectProfileEntity entity = projectProfileMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        projectPermissionService.requireProjectAccess(id);
        return toResponse(entity);
    }

    public List<ProjectDetailResponse> list() {
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        if (!applyProjectScope(wrapper)) {
            return new ArrayList<ProjectDetailResponse>();
        }
        wrapper.orderByDesc("id");
        List<ProjectProfileEntity> entities = projectProfileMapper.selectList(wrapper);
        List<ProjectDetailResponse> responses = new ArrayList<ProjectDetailResponse>();
        for (ProjectProfileEntity entity : entities) {
            responses.add(toResponse(entity));
        }
        return responses;
    }

    public ProjectPageResponse page(ProjectQueryRequest request) {
        long pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        long pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        if (!applyProjectScope(wrapper)) {
            return emptyPage(pageNo, pageSize);
        }
        if (StringUtils.hasText(request.getProjectName())) {
            wrapper.like("project_name", request.getProjectName().trim());
        }
        if (StringUtils.hasText(request.getGitlabProjectUrl())) {
            wrapper.like("gitlab_project_url", request.getGitlabProjectUrl().trim());
        }
        if (request.getAiReviewEnabled() != null) {
            wrapper.eq("ai_review_enabled", request.getAiReviewEnabled());
        }
        if (request.getWecomNotifyEnabled() != null) {
            wrapper.eq("wecom_notify_enabled", request.getWecomNotifyEnabled());
        }
        wrapper.orderByDesc("id");
        Page<ProjectProfileEntity> page = projectProfileMapper.selectPage(new Page<ProjectProfileEntity>(pageNo, pageSize), wrapper);

        ProjectPageResponse response = emptyPage(pageNo, pageSize);
        response.setTotal(page.getTotal());
        List<ProjectDetailResponse> records = new ArrayList<ProjectDetailResponse>();
        for (ProjectProfileEntity entity : page.getRecords()) {
            records.add(toResponse(entity));
        }
        response.setRecords(records);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProjectProfileEntity entity = projectProfileMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        requireProjectManager(entity);
        projectProfileMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDetailResponse refresh(Long id) {
        ProjectProfileEntity entity = projectProfileMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        requireProjectManager(entity);
        fillGitLabProjectInfo(entity, entity.getGitlabProjectUrl(), entity.getGitlabWebhookToken());
        entity.setUpdatedAt(new Date());
        projectProfileMapper.updateById(entity);
        return toResponse(entity);
    }

    public List<ProjectLlmModelOptionResponse> listAvailableLlmModels(Long projectId) {
        ProjectProfileEntity entity = projectProfileMapper.selectById(projectId);
        if (entity == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        return llmModelService.listProjectAvailableModels(projectId);
    }

    private void validateRequest(ProjectUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getProjectName())) {
            throw new DomainException("PROJECT_NAME_REQUIRED", "Project name is required");
        }
        if (!StringUtils.hasText(request.getGitlabProjectUrl())) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required");
        }
        if (!StringUtils.hasText(request.getGitlabWebhookToken())) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required");
        }
        if (Boolean.TRUE.equals(request.getAiReviewEnabled()) && request.getLlmModelId() == null) {
            throw new DomainException("PROJECT_LLM_MODEL_REQUIRED", "AI review enabled project must select a model");
        }
        if (Boolean.TRUE.equals(request.getWecomNotifyEnabled()) && !StringUtils.hasText(request.getWecomWebhookUrl())) {
            throw new DomainException("WECOM_WEBHOOK_REQUIRED", "WeCom webhook URL is required when notification is enabled");
        }
        if (request.getTemplateId() != null && projectTemplateMapper.selectById(request.getTemplateId()) == null) {
            throw new DomainException("PROJECT_TEMPLATE_NOT_FOUND", "Project template not found");
        }
    }

    private void applyRequest(ProjectProfileEntity entity, ProjectUpsertRequest request) {
        entity.setProjectName(request.getProjectName().trim());
        entity.setSourcePlatform(StringUtils.hasText(request.getSourcePlatform())
            ? request.getSourcePlatform().trim() : "gitlab");
        entity.setGitlabProjectUrl(request.getGitlabProjectUrl().trim());
        entity.setGitlabWebhookToken(normalizeText(request.getGitlabWebhookToken()));
        entity.setReviewBranches(normalizeText(request.getReviewBranches()));
        fillGitLabProjectInfo(entity, request.getGitlabProjectUrl(), request.getGitlabWebhookToken());
        entity.setTemplateId(request.getTemplateId());
        entity.setSupportedFileExtensions(normalizeText(request.getSupportedFileExtensions()));
        entity.setAiReviewEnabled(request.getAiReviewEnabled() == null ? Boolean.TRUE : request.getAiReviewEnabled());
        entity.setReviewContextEnabled(request.getReviewContextEnabled() == null ? Boolean.TRUE : request.getReviewContextEnabled());
        entity.setGitlabNoteEnabled(request.getGitlabNoteEnabled() == null ? Boolean.TRUE : request.getGitlabNoteEnabled());
        entity.setWecomNotifyEnabled(request.getWecomNotifyEnabled() == null ? Boolean.FALSE : request.getWecomNotifyEnabled());
        entity.setWecomWebhookUrl(normalizeText(request.getWecomWebhookUrl()));
        entity.setPromptContent(normalizeText(request.getPromptContent()));
        entity.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
    }

    private void fillGitLabProjectInfo(ProjectProfileEntity entity, String gitlabProjectUrl, String token) {
        GitLabProjectPayload payload = gitLabProjectResolver.resolveProject(gitlabProjectUrl, token);
        entity.setGitlabProjectId(payload.getId());
        if (StringUtils.hasText(payload.getWebUrl())) {
            entity.setGitlabProjectUrl(payload.getWebUrl());
        }
    }

    private Long resolveCreateOwnerUserId(ProjectUpsertRequest request, Long currentUserId, boolean admin) {
        if (!admin) {
            return currentUserId;
        }
        if (request.getOwnerUserId() == null) {
            throw new DomainException("PROJECT_OWNER_REQUIRED", "Project owner is required");
        }
        return request.getOwnerUserId();
    }

    private Long resolveUpdateOwnerUserId(ProjectUpsertRequest request) {
        if (request.getOwnerUserId() == null) {
            throw new DomainException("PROJECT_OWNER_REQUIRED", "Project owner is required");
        }
        return request.getOwnerUserId();
    }

    private List<Long> normalizeMemberUserIds(List<Long> requestMemberUserIds, Long ownerUserId, Long requiredMemberUserId) {
        Set<Long> uniqueUserIds = new LinkedHashSet<Long>();
        if (!CollectionUtils.isEmpty(requestMemberUserIds)) {
            for (Long userId : requestMemberUserIds) {
                if (userId != null) {
                    uniqueUserIds.add(userId);
                }
            }
        }
        if (requiredMemberUserId != null) {
            uniqueUserIds.add(requiredMemberUserId);
        }
        if (ownerUserId != null) {
            uniqueUserIds.add(ownerUserId);
        }
        if (uniqueUserIds.isEmpty()) {
            throw new DomainException("PROJECT_MEMBER_REQUIRED", "Project members are required");
        }
        return new ArrayList<Long>(uniqueUserIds);
    }

    private void validateProjectParticipants(Long ownerUserId, List<Long> memberUserIds) {
        if (ownerUserId == null) {
            throw new DomainException("PROJECT_OWNER_REQUIRED", "Project owner is required");
        }
        if (CollectionUtils.isEmpty(memberUserIds)) {
            throw new DomainException("PROJECT_MEMBER_REQUIRED", "Project members are required");
        }
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.in("id", memberUserIds);
        List<UserEntity> users = userMapper.selectList(wrapper);
        if (users.size() != memberUserIds.size()) {
            throw new DomainException("PROJECT_MEMBER_NOT_FOUND", "Project members contain invalid user");
        }
        boolean ownerExists = false;
        for (UserEntity user : users) {
            if (!"ENABLE".equals(user.getStatus())) {
                throw new DomainException("PROJECT_MEMBER_DISABLED", "Project members must be enabled users");
            }
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new DomainException("PROJECT_MEMBER_ADMIN_UNSUPPORTED", "Admin users cannot be assigned as project members or owner");
            }
            if (ownerUserId.equals(user.getId())) {
                ownerExists = true;
            }
        }
        if (!ownerExists) {
            throw new DomainException("PROJECT_OWNER_NOT_MEMBER", "Project owner must be an assigned project member");
        }
    }

    private void syncProjectMembers(Long projectId, List<Long> memberUserIds) {
        QueryWrapper<UserProjectRelEntity> deleteWrapper = new QueryWrapper<UserProjectRelEntity>();
        deleteWrapper.eq("project_id", projectId);
        userProjectRelMapper.delete(deleteWrapper);

        Date now = new Date();
        for (Long userId : memberUserIds) {
            UserProjectRelEntity relation = new UserProjectRelEntity();
            relation.setUserId(userId);
            relation.setProjectId(projectId);
            relation.setCreatedAt(now);
            userProjectRelMapper.insert(relation);
        }
    }

    private void requireProjectManager(ProjectProfileEntity entity) {
        if (currentUserService.isAdmin()) {
            return;
        }
        if (entity.getOwnerUserId() != null && entity.getOwnerUserId().equals(currentUserService.requireCurrentUserId())) {
            return;
        }
        throw new DomainException("AUTH_FORBIDDEN", "Only admin or project owner can update project owner and members");
    }

    private String buildProjectKey() {
        return "project:" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean applyProjectScope(QueryWrapper<ProjectProfileEntity> wrapper) {
        if (currentUserService.isAdmin()) {
            return true;
        }
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        if (accessibleProjectIds.isEmpty()) {
            return false;
        }
        wrapper.in("id", accessibleProjectIds);
        return true;
    }

    private ProjectPageResponse emptyPage(long pageNo, long pageSize) {
        ProjectPageResponse response = new ProjectPageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(0);
        response.setRecords(new ArrayList<ProjectDetailResponse>());
        return response;
    }

    private ProjectDetailResponse toResponse(ProjectProfileEntity entity) {
        ProjectDetailResponse response = new ProjectDetailResponse();
        response.setId(entity.getId());
        response.setProjectKey(entity.getProjectKey());
        response.setProjectName(entity.getProjectName());
        response.setSourcePlatform(entity.getSourcePlatform());
        response.setGitlabProjectId(entity.getGitlabProjectId());
        response.setGitlabProjectUrl(entity.getGitlabProjectUrl());
        response.setGitlabWebhookToken(entity.getGitlabWebhookToken());
        response.setReviewBranches(entity.getReviewBranches());
        response.setOwnerUserId(entity.getOwnerUserId());
        response.setTemplateId(entity.getTemplateId());
        response.setTemplateName(resolveTemplateName(entity.getTemplateId()));
        response.setSupportedFileExtensions(entity.getSupportedFileExtensions());
        response.setLlmModelId(entity.getLlmModelId());
        response.setAiReviewEnabled(entity.getAiReviewEnabled());
        response.setReviewContextEnabled(entity.getReviewContextEnabled());
        response.setGitlabNoteEnabled(entity.getGitlabNoteEnabled());
        response.setWecomNotifyEnabled(entity.getWecomNotifyEnabled());
        response.setWecomWebhookUrl(entity.getWecomWebhookUrl());
        response.setPromptContent(entity.getPromptContent());
        response.setActive(entity.getActive());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String resolveTemplateName(Long templateId) {
        if (templateId == null) {
            return null;
        }
        ProjectTemplateEntity template = projectTemplateMapper.selectById(templateId);
        return template == null ? null : template.getTemplateName();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
