package com.vemo.codereview.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.llm.entity.LlmProviderConfigEntity;
import com.vemo.codereview.llm.mapper.LlmModelProjectRelMapper;
import com.vemo.codereview.llm.mapper.LlmProviderConfigMapper;
import com.vemo.codereview.llm.model.LlmRuntimeConfig;
import com.vemo.codereview.llm.service.LlmConfigResolverService;
import com.vemo.codereview.project.service.ProjectConfigService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmConfigResolverServiceTest {

    @Mock
    private LlmProviderConfigMapper llmProviderConfigMapper;

    @Mock
    private LlmModelProjectRelMapper llmModelProjectRelMapper;

    @Mock
    private ProjectConfigService projectConfigService;

    @Test
    void shouldCopyThinkingEnabledToRuntimeConfig() {
        LlmConfigResolverService resolverService = new LlmConfigResolverService(
            llmProviderConfigMapper,
            llmModelProjectRelMapper,
            projectConfigService
        );
        ProjectProfileEntity project = new ProjectProfileEntity();
        project.setId(1001L);
        project.setLlmModelId(2001L);
        LlmProviderConfigEntity modelConfig = buildModelConfig();
        modelConfig.setThinkingEnabled(Boolean.TRUE);
        when(projectConfigService.findById(1001L)).thenReturn(project);
        when(llmProviderConfigMapper.selectById(2001L)).thenReturn(modelConfig);

        LlmRuntimeConfig runtimeConfig = resolverService.resolve(1001L);

        assertTrue(runtimeConfig.getThinkingEnabled());
        assertEquals("DEEPSEEK", runtimeConfig.getProviderCode());
        assertEquals("deepseek-chat", runtimeConfig.getModelName());
        assertFalse(runtimeConfig.isFallbackConfig());
    }

    private LlmProviderConfigEntity buildModelConfig() {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setId(2001L);
        entity.setProviderCode("DEEPSEEK");
        entity.setProviderType("OPENAI_COMPATIBLE");
        entity.setBaseUrl("https://api.deepseek.com");
        entity.setApiKey("llm-key");
        entity.setModelName("deepseek-chat");
        entity.setEnabled(Boolean.TRUE);
        entity.setScopeType("GLOBAL");
        entity.setTimeoutMs(30000);
        entity.setMaxTokens(2048);
        entity.setTemperature(new BigDecimal("0.1"));
        return entity;
    }
}
