package com.vemo.codereview.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.llm.entity.LlmModelProjectRelEntity;
import com.vemo.codereview.llm.entity.LlmProviderConfigEntity;
import com.vemo.codereview.llm.mapper.LlmModelProjectRelMapper;
import com.vemo.codereview.llm.mapper.LlmProviderConfigMapper;
import com.vemo.codereview.llm.model.ChatCompletionRequest;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.llm.model.LlmModelDetailResponse;
import com.vemo.codereview.llm.model.LlmModelPageResponse;
import com.vemo.codereview.llm.model.LlmModelQueryRequest;
import com.vemo.codereview.llm.model.LlmModelTestResponse;
import com.vemo.codereview.llm.model.LlmModelUpsertRequest;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.llm.model.ProjectLlmModelOptionResponse;
import com.vemo.codereview.project.service.ProjectPermissionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class LlmModelService {

    private static final int DEFAULT_TIMEOUT_MS = 180000;
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final java.math.BigDecimal DEFAULT_TEMPERATURE = new java.math.BigDecimal("0.1");

    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final LlmModelProjectRelMapper llmModelProjectRelMapper;
    private final ProjectProfileMapper projectProfileMapper;
    private final CurrentUserService currentUserService;
    private final ProjectPermissionService projectPermissionService;
    private final ChatModelClientFactory chatModelClientFactory;

    public LlmModelService(
        LlmProviderConfigMapper llmProviderConfigMapper,
        LlmModelProjectRelMapper llmModelProjectRelMapper,
        ProjectProfileMapper projectProfileMapper,
        CurrentUserService currentUserService,
        ProjectPermissionService projectPermissionService,
        ChatModelClientFactory chatModelClientFactory) {
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.llmModelProjectRelMapper = llmModelProjectRelMapper;
        this.projectProfileMapper = projectProfileMapper;
        this.currentUserService = currentUserService;
        this.projectPermissionService = projectPermissionService;
        this.chatModelClientFactory = chatModelClientFactory;
    }

    public LlmModelPageResponse page(LlmModelQueryRequest request) {
        long pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        long pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        QueryWrapper<LlmProviderConfigEntity> wrapper = new QueryWrapper<LlmProviderConfigEntity>();
        if (StringUtils.hasText(request.getConfigName())) {
            wrapper.like("config_name", request.getConfigName().trim());
        }
        if (StringUtils.hasText(request.getProviderCode())) {
            wrapper.eq("provider_code", request.getProviderCode().trim().toUpperCase());
        }
        if (StringUtils.hasText(request.getScopeType())) {
            wrapper.eq("scope_type", request.getScopeType().trim().toUpperCase());
        }
        if (request.getEnabled() != null) {
            wrapper.eq("enabled", request.getEnabled() ? 1 : 0);
        }
        applyVisibilityScope(wrapper);
        wrapper.orderByDesc("id");

        Page<LlmProviderConfigEntity> page = llmProviderConfigMapper.selectPage(new Page<LlmProviderConfigEntity>(pageNo, pageSize), wrapper);
        Map<Long, String> projectNameMap = loadProjectNameMap(page.getRecords());

        LlmModelPageResponse response = new LlmModelPageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotal());
        for (LlmProviderConfigEntity entity : page.getRecords()) {
            LlmModelPageResponse.Item item = new LlmModelPageResponse.Item();
            item.setId(entity.getId());
            item.setConfigName(entity.getConfigName());
            item.setProviderCode(entity.getProviderCode());
            item.setProviderName(entity.getProviderName());
            item.setProviderType(entity.getProviderType());
            item.setApiKeyMasked(maskApiKey(entity.getApiKey()));
            item.setModelName(entity.getModelName());
            item.setScopeType(entity.getScopeType());
            item.setMaintainerProjectId(entity.getMaintainerProjectId());
            item.setMaintainerProjectName(projectNameMap.get(entity.getMaintainerProjectId()));
            item.setManageable(canManage(entity));
            item.setEnabled(entity.getEnabled());
            item.setUpdatedAt(entity.getUpdatedAt());
            response.getRecords().add(item);
        }
        return response;
    }

    public LlmModelDetailResponse getById(Long id) {
        LlmProviderConfigEntity entity = requireModel(id);
        requireVisible(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LlmModelDetailResponse create(LlmModelUpsertRequest request) {
        validateUpsertRequest(request, true);
        String scopeType = normalizeScopeType(request.getScopeType());
        requireCreatePermission(scopeType, request.getMaintainerProjectId());
        Date now = new Date();
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        applyUpsert(entity, request, true);
        entity.setScopeType(scopeType);
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        llmProviderConfigMapper.insert(entity);
        syncProjectRelations(entity, request.getProjectIds());
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LlmModelDetailResponse update(Long id, LlmModelUpsertRequest request) {
        validateUpsertRequest(request, false);
        LlmProviderConfigEntity entity = requireModel(id);
        requireManage(entity);
        String scopeType = normalizeScopeType(request.getScopeType());
        requireCreatePermission(scopeType, request.getMaintainerProjectId());
        applyUpsert(entity, request, false);
        entity.setScopeType(scopeType);
        entity.setUpdatedAt(new Date());
        llmProviderConfigMapper.updateById(entity);
        syncProjectRelations(entity, request.getProjectIds());
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LlmModelDetailResponse enable(Long id) {
        LlmProviderConfigEntity entity = requireModel(id);
        requireManage(entity);
        entity.setEnabled(true);
        entity.setUpdatedAt(new Date());
        llmProviderConfigMapper.updateById(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LlmModelDetailResponse disable(Long id) {
        LlmProviderConfigEntity entity = requireModel(id);
        requireManage(entity);
        entity.setEnabled(false);
        entity.setUpdatedAt(new Date());
        llmProviderConfigMapper.updateById(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LlmProviderConfigEntity entity = requireModel(id);
        requireManage(entity);
        QueryWrapper<ProjectProfileEntity> projectWrapper = new QueryWrapper<ProjectProfileEntity>();
        projectWrapper.eq("llm_model_id", id)
            .select("id", "project_name")
            .last("limit 1");
        ProjectProfileEntity boundProject = projectProfileMapper.selectOne(projectWrapper);
        if (boundProject != null) {
            String projectName = StringUtils.hasText(boundProject.getProjectName()) ? boundProject.getProjectName() : String.valueOf(boundProject.getId());
            throw new DomainException("LLM_MODEL_IN_USE", "Model is already bound to project: " + projectName);
        }

        QueryWrapper<LlmModelProjectRelEntity> relWrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        relWrapper.eq("llm_model_id", id);
        llmModelProjectRelMapper.delete(relWrapper);
        llmProviderConfigMapper.deleteById(id);
    }

    public LlmModelTestResponse test(Long id) {
        LlmProviderConfigEntity entity = requireModel(id);
        requireManage(entity);

        LlmRuntimeConfig runtimeConfig = toRuntimeConfig(entity);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(runtimeConfig.getModelName());
        request.setTemperature(runtimeConfig.getTemperature() == null ? null : runtimeConfig.getTemperature().doubleValue());
        request.setMaxTokens(runtimeConfig.getMaxTokens());
        List<ChatCompletionRequest.Message> messages = new ArrayList<ChatCompletionRequest.Message>();
        messages.add(new ChatCompletionRequest.Message("system", "You are a connection test assistant."));
        messages.add(new ChatCompletionRequest.Message("user", "Reply with OK only."));
        request.setMessages(messages);

        ChatCompletionResponse response = chatModelClientFactory.getClient(runtimeConfig.getProviderType())
            .chatCompletion(request, runtimeConfig);

        LlmModelTestResponse testResponse = new LlmModelTestResponse();
        testResponse.setProviderType(runtimeConfig.getProviderType());
        testResponse.setModelName(runtimeConfig.getModelName());
        testResponse.setResponseModel(response.getModel());
        testResponse.setMessage(extractMessage(response));
        return testResponse;
    }

    public void requireProjectModelUsable(Long projectId, Long llmModelId) {
        if (llmModelId == null) {
            return;
        }
        projectPermissionService.requireProjectAccess(projectId);
        LlmProviderConfigEntity entity = requireModel(llmModelId);
        if (!isProjectModelUsable(projectId, entity)) {
            throw new DomainException("LLM_MODEL_PROJECT_ACCESS_DENIED", "Project cannot use the selected model");
        }
    }

    public List<ProjectLlmModelOptionResponse> listProjectAvailableModels(Long projectId) {
        projectPermissionService.requireProjectAccess(projectId);
        QueryWrapper<LlmProviderConfigEntity> wrapper = new QueryWrapper<LlmProviderConfigEntity>();
        wrapper.eq("enabled", 1).orderByAsc("config_name");
        List<LlmProviderConfigEntity> entities = llmProviderConfigMapper.selectList(wrapper);
        List<ProjectLlmModelOptionResponse> responses = new ArrayList<ProjectLlmModelOptionResponse>();
        for (LlmProviderConfigEntity entity : entities) {
            if (!isProjectModelUsable(projectId, entity)) {
                continue;
            }
            ProjectLlmModelOptionResponse item = new ProjectLlmModelOptionResponse();
            item.setId(entity.getId());
            item.setConfigName(entity.getConfigName());
            item.setProviderCode(entity.getProviderCode());
            item.setModelName(entity.getModelName());
            item.setScopeType(entity.getScopeType());
            responses.add(item);
        }
        return responses;
    }

    private boolean isProjectModelUsable(Long projectId, LlmProviderConfigEntity entity) {
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            return false;
        }
        if ("GLOBAL".equalsIgnoreCase(entity.getScopeType())) {
            return true;
        }
        if (!"PROJECT".equalsIgnoreCase(entity.getScopeType())) {
            return false;
        }
        if (projectId != null && projectId.equals(entity.getMaintainerProjectId())) {
            return true;
        }
        QueryWrapper<LlmModelProjectRelEntity> wrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        wrapper.eq("llm_model_id", entity.getId())
            .eq("project_id", projectId)
            .last("limit 1");
        return llmModelProjectRelMapper.selectOne(wrapper) != null;
    }

    private String extractMessage(ChatCompletionResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return "Connection succeeded";
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null || !StringUtils.hasText(choice.getMessage().getContent())) {
            return "Connection succeeded";
        }
        return choice.getMessage().getContent();
    }

    private void applyVisibilityScope(QueryWrapper<LlmProviderConfigEntity> wrapper) {
        if (currentUserService.isAdmin()) {
            return;
        }
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        if (CollectionUtils.isEmpty(accessibleProjectIds)) {
            wrapper.eq("scope_type", "GLOBAL");
            return;
        }
        String joinedProjectIds = joinIds(accessibleProjectIds);
        wrapper.and(w -> w.eq("scope_type", "GLOBAL")
            .or(o -> o.eq("scope_type", "PROJECT").in("maintainer_project_id", accessibleProjectIds))
            .or(o -> o.eq("scope_type", "PROJECT").inSql("id", "select llm_model_id from llm_model_project_rel where project_id in (" + joinedProjectIds + ")")));
    }

    private void requireVisible(LlmProviderConfigEntity entity) {
        if (currentUserService.isAdmin()) {
            return;
        }
        if ("GLOBAL".equalsIgnoreCase(entity.getScopeType())) {
            return;
        }
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        if (accessibleProjectIds.isEmpty()) {
            throw new DomainException("AUTH_FORBIDDEN", "Current user cannot access this model configuration");
        }
        if (accessibleProjectIds.contains(entity.getMaintainerProjectId())) {
            return;
        }
        QueryWrapper<LlmModelProjectRelEntity> wrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        wrapper.eq("llm_model_id", entity.getId())
            .in("project_id", accessibleProjectIds)
            .last("limit 1");
        if (llmModelProjectRelMapper.selectOne(wrapper) != null) {
            return;
        }
        throw new DomainException("AUTH_FORBIDDEN", "Current user cannot access this model configuration");
    }

    private void requireManage(LlmProviderConfigEntity entity) {
        if (canManage(entity)) {
            return;
        }
        if (!"PROJECT".equalsIgnoreCase(entity.getScopeType())) {
            throw new DomainException("AUTH_FORBIDDEN", "Only admin can manage this model configuration");
        }
        projectPermissionService.requireProjectOwner(entity.getMaintainerProjectId());
    }

    private boolean canManage(LlmProviderConfigEntity entity) {
        if (currentUserService.isAdmin()) {
            return true;
        }
        if (!"PROJECT".equalsIgnoreCase(entity.getScopeType()) || entity.getMaintainerProjectId() == null) {
            return false;
        }
        Long currentUserId = currentUserService.requireCurrentUserId();
        ProjectProfileEntity project = projectProfileMapper.selectById(entity.getMaintainerProjectId());
        return project != null && currentUserId.equals(project.getOwnerUserId());
    }

    private void requireCreatePermission(String scopeType, Long maintainerProjectId) {
        if ("GLOBAL".equals(scopeType)) {
            if (!currentUserService.isAdmin()) {
                throw new DomainException("AUTH_FORBIDDEN", "Only admin can manage global models");
            }
            return;
        }
        if (maintainerProjectId == null) {
            throw new DomainException("LLM_MODEL_MAINTAINER_PROJECT_REQUIRED", "Maintainer project is required for project models");
        }
        if (currentUserService.isAdmin()) {
            return;
        }
        projectPermissionService.requireProjectOwner(maintainerProjectId);
    }

    private void validateUpsertRequest(LlmModelUpsertRequest request, boolean create) {
        if (request == null) {
            throw new DomainException("LLM_MODEL_PARAM_INVALID", "Model request is required");
        }
        if (!StringUtils.hasText(request.getConfigName())) {
            throw new DomainException("LLM_MODEL_CONFIG_NAME_REQUIRED", "Config name is required");
        }
        if (!StringUtils.hasText(request.getProviderCode())) {
            throw new DomainException("LLM_MODEL_PROVIDER_CODE_REQUIRED", "Provider code is required");
        }
        if (!StringUtils.hasText(request.getProviderName())) {
            throw new DomainException("LLM_MODEL_PROVIDER_NAME_REQUIRED", "Provider name is required");
        }
        if (!StringUtils.hasText(request.getProviderType())) {
            throw new DomainException("LLM_MODEL_PROVIDER_TYPE_REQUIRED", "Provider type is required");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new DomainException("LLM_MODEL_BASE_URL_REQUIRED", "Base URL is required");
        }
        if (create && !StringUtils.hasText(request.getApiKey())) {
            throw new DomainException("LLM_MODEL_API_KEY_REQUIRED", "API key is required");
        }
        if (!StringUtils.hasText(request.getModelName())) {
            throw new DomainException("LLM_MODEL_NAME_REQUIRED", "Model name is required");
        }
        normalizeProviderType(request.getProviderType());
        normalizeScopeType(request.getScopeType());
    }

    private void applyUpsert(LlmProviderConfigEntity entity, LlmModelUpsertRequest request, boolean create) {
        entity.setConfigName(request.getConfigName().trim());
        entity.setProviderCode(request.getProviderCode().trim().toUpperCase());
        entity.setProviderName(request.getProviderName().trim());
        entity.setProviderType(normalizeProviderType(request.getProviderType()));
        entity.setBaseUrl(request.getBaseUrl().trim());
        if (create || StringUtils.hasText(request.getApiKey())) {
            entity.setApiKey(StringUtils.hasText(request.getApiKey()) ? request.getApiKey().trim() : null);
        }
        entity.setModelName(request.getModelName().trim());
        entity.setEnabled(request.getEnabled() == null ? entity.getEnabled() : request.getEnabled());
        entity.setScopeType(normalizeScopeType(request.getScopeType()));
        entity.setMaintainerProjectId("PROJECT".equalsIgnoreCase(entity.getScopeType()) ? request.getMaintainerProjectId() : null);
        entity.setTimeoutMs(request.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : request.getTimeoutMs());
        entity.setMaxTokens(request.getMaxTokens() == null ? DEFAULT_MAX_TOKENS : request.getMaxTokens());
        entity.setTemperature(request.getTemperature() == null ? DEFAULT_TEMPERATURE : request.getTemperature());
        entity.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    private void syncProjectRelations(LlmProviderConfigEntity entity, List<Long> requestedProjectIds) {
        QueryWrapper<LlmModelProjectRelEntity> deleteWrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        deleteWrapper.eq("llm_model_id", entity.getId());
        llmModelProjectRelMapper.delete(deleteWrapper);

        if (!"PROJECT".equalsIgnoreCase(entity.getScopeType())) {
            return;
        }

        Set<Long> projectIds = new LinkedHashSet<Long>();
        if (!CollectionUtils.isEmpty(requestedProjectIds)) {
            projectIds.addAll(requestedProjectIds);
        }
        if (entity.getMaintainerProjectId() != null) {
            projectIds.add(entity.getMaintainerProjectId());
        }
        validateProjectIds(projectIds);

        Date now = new Date();
        for (Long projectId : projectIds) {
            LlmModelProjectRelEntity relation = new LlmModelProjectRelEntity();
            relation.setLlmModelId(entity.getId());
            relation.setProjectId(projectId);
            relation.setCreatedAt(now);
            llmModelProjectRelMapper.insert(relation);
        }
    }

    private void validateProjectIds(Set<Long> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return;
        }
        if (!currentUserService.isAdmin()) {
            List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
            if (!accessibleProjectIds.containsAll(projectIds)) {
                throw new DomainException("PROJECT_ACCESS_DENIED", "Contains project outside current user scope");
            }
        }
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.in("id", projectIds).select("id");
        List<ProjectProfileEntity> projects = projectProfileMapper.selectList(wrapper);
        if (projects.size() != projectIds.size()) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project contains invalid id");
        }
    }

    private LlmProviderConfigEntity requireModel(Long id) {
        LlmProviderConfigEntity entity = llmProviderConfigMapper.selectById(id);
        if (entity == null) {
            throw new DomainException("LLM_MODEL_NOT_FOUND", "Model configuration not found");
        }
        return entity;
    }

    private LlmModelDetailResponse toDetailResponse(LlmProviderConfigEntity entity) {
        LlmModelDetailResponse response = new LlmModelDetailResponse();
        response.setId(entity.getId());
        response.setConfigName(entity.getConfigName());
        response.setProviderCode(entity.getProviderCode());
        response.setProviderName(entity.getProviderName());
        response.setProviderType(entity.getProviderType());
        response.setBaseUrl(entity.getBaseUrl());
        response.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        response.setModelName(entity.getModelName());
        response.setEnabled(entity.getEnabled());
        response.setScopeType(entity.getScopeType());
        response.setMaintainerProjectId(entity.getMaintainerProjectId());
        response.setMaintainerProjectName(resolveProjectName(entity.getMaintainerProjectId()));
        response.setManageable(canManage(entity));
        response.setTimeoutMs(entity.getTimeoutMs());
        response.setMaxTokens(entity.getMaxTokens());
        response.setTemperature(entity.getTemperature());
        response.setRemark(entity.getRemark());
        response.setProjectIds(loadProjectIds(entity.getId()));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private List<Long> loadProjectIds(Long modelId) {
        QueryWrapper<LlmModelProjectRelEntity> wrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        wrapper.eq("llm_model_id", modelId).orderByAsc("id");
        List<LlmModelProjectRelEntity> relations = llmModelProjectRelMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(relations)) {
            return new ArrayList<Long>();
        }
        List<Long> ids = new ArrayList<Long>(relations.size());
        for (LlmModelProjectRelEntity relation : relations) {
            ids.add(relation.getProjectId());
        }
        return ids;
    }

    private Map<Long, String> loadProjectNameMap(List<LlmProviderConfigEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyMap();
        }
        Set<Long> projectIds = new HashSet<Long>();
        for (LlmProviderConfigEntity entity : entities) {
            if (entity.getMaintainerProjectId() != null) {
                projectIds.add(entity.getMaintainerProjectId());
            }
        }
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptyMap();
        }
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.in("id", projectIds).select("id", "project_name");
        List<ProjectProfileEntity> projects = projectProfileMapper.selectList(wrapper);
        Map<Long, String> projectNameMap = new HashMap<Long, String>();
        for (ProjectProfileEntity project : projects) {
            projectNameMap.put(project.getId(), project.getProjectName());
        }
        return projectNameMap;
    }

    private String resolveProjectName(Long projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectProfileEntity project = projectProfileMapper.selectById(projectId);
        return project == null ? null : project.getProjectName();
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String value = apiKey.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String normalizeProviderType(String providerType) {
        String normalized = StringUtils.hasText(providerType) ? providerType.trim().toUpperCase() : null;
        if (!"OPENAI_COMPATIBLE".equals(normalized) && !"ANTHROPIC".equals(normalized)) {
            throw new DomainException("LLM_MODEL_PROVIDER_TYPE_INVALID", "Provider type is invalid");
        }
        return normalized;
    }

    private String normalizeScopeType(String scopeType) {
        String normalized = StringUtils.hasText(scopeType) ? scopeType.trim().toUpperCase() : null;
        if (!"GLOBAL".equals(normalized) && !"PROJECT".equals(normalized)) {
            throw new DomainException("LLM_MODEL_SCOPE_TYPE_INVALID", "Scope type is invalid");
        }
        return normalized;
    }

    private String joinIds(List<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids.get(i));
        }
        return builder.toString();
    }

    private LlmRuntimeConfig toRuntimeConfig(LlmProviderConfigEntity entity) {
        LlmRuntimeConfig runtimeConfig = new LlmRuntimeConfig();
        runtimeConfig.setModelConfigId(entity.getId());
        runtimeConfig.setProviderCode(entity.getProviderCode());
        runtimeConfig.setProviderType(entity.getProviderType());
        runtimeConfig.setBaseUrl(entity.getBaseUrl());
        runtimeConfig.setApiKey(entity.getApiKey());
        runtimeConfig.setModelName(entity.getModelName());
        runtimeConfig.setTimeoutMs(entity.getTimeoutMs());
        runtimeConfig.setMaxTokens(entity.getMaxTokens());
        runtimeConfig.setTemperature(entity.getTemperature());
        return runtimeConfig;
    }
}
