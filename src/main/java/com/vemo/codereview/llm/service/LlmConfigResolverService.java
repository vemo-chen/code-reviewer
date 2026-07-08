package com.vemo.codereview.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.llm.entity.LlmModelProjectRelEntity;
import com.vemo.codereview.llm.entity.LlmProviderConfigEntity;
import com.vemo.codereview.llm.mapper.LlmModelProjectRelMapper;
import com.vemo.codereview.llm.mapper.LlmProviderConfigMapper;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.project.service.ProjectConfigService;
import org.springframework.stereotype.Service;

@Service
public class LlmConfigResolverService {

    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final LlmModelProjectRelMapper llmModelProjectRelMapper;
    private final ProjectConfigService projectConfigService;

    public LlmConfigResolverService(
        LlmProviderConfigMapper llmProviderConfigMapper,
        LlmModelProjectRelMapper llmModelProjectRelMapper,
        ProjectConfigService projectConfigService) {
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.llmModelProjectRelMapper = llmModelProjectRelMapper;
        this.projectConfigService = projectConfigService;
    }

    public LlmRuntimeConfig resolve(Long projectId) {
        if (projectId == null) {
            throw new DomainException("LLM_MODEL_REQUIRED", "AI review requires an explicitly bound model");
        }
        ProjectProfileEntity project = projectConfigService.findById(projectId);
        if (project == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project does not exist");
        }
        if (project.getLlmModelId() == null) {
            throw new DomainException("LLM_MODEL_REQUIRED", "AI review enabled project must select a model");
        }
        LlmProviderConfigEntity projectModel = llmProviderConfigMapper.selectById(project.getLlmModelId());
        if (projectModel == null) {
            throw new DomainException("LLM_MODEL_NOT_FOUND", "Project bound model does not exist");
        }
        validateProjectModelBinding(projectId, projectModel);
        return toRuntimeConfig(projectModel, false);
    }

    private void validateProjectModelBinding(Long projectId, LlmProviderConfigEntity modelConfig) {
        if (!Boolean.TRUE.equals(modelConfig.getEnabled())) {
            throw new DomainException("LLM_MODEL_DISABLED", "Project bound model is disabled");
        }

        if ("GLOBAL".equalsIgnoreCase(modelConfig.getScopeType())) {
            return;
        }

        if (!"PROJECT".equalsIgnoreCase(modelConfig.getScopeType())) {
            throw new DomainException("LLM_MODEL_SCOPE_INVALID", "Project bound model scope is invalid");
        }

        if (projectId.equals(modelConfig.getMaintainerProjectId())) {
            return;
        }

        QueryWrapper<LlmModelProjectRelEntity> relationWrapper = new QueryWrapper<LlmModelProjectRelEntity>();
        relationWrapper.eq("llm_model_id", modelConfig.getId())
            .eq("project_id", projectId)
            .last("limit 1");
        LlmModelProjectRelEntity relation = llmModelProjectRelMapper.selectOne(relationWrapper);
        if (relation == null) {
            throw new DomainException("LLM_MODEL_PROJECT_ACCESS_DENIED", "Project is not allowed to use the bound model");
        }
    }

    private LlmRuntimeConfig toRuntimeConfig(LlmProviderConfigEntity entity, boolean fallbackConfig) {
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
        runtimeConfig.setThinkingEnabled(Boolean.TRUE.equals(entity.getThinkingEnabled()));
        runtimeConfig.setFallbackConfig(fallbackConfig);
        return runtimeConfig;
    }
}
