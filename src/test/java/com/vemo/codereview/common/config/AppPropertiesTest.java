package com.vemo.codereview.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AppPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:app-properties-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "code-reviewer.app.async.core-pool-size=2",
    "code-reviewer.app.async.max-pool-size=6",
    "code-reviewer.app.async.queue-capacity=50",
    "code-reviewer.app.async.thread-name-prefix=test-review-",
    "code-reviewer.gitlab.url=https://gitlab.example.com",
    "code-reviewer.gitlab.token=gitlab-access-token",
    "code-reviewer.gitlab.connect-timeout-ms=7000",
    "code-reviewer.gitlab.read-timeout-ms=12000",
    "code-reviewer.llm.base-url=https://api.example.com",
    "code-reviewer.llm.api-key=llm-key",
    "code-reviewer.llm.model=deepseek-chat",
    "code-reviewer.llm.timeout-ms=45000",
    "code-reviewer.llm.max-tokens=2048",
    "code-reviewer.llm.temperature=0.1",
    "code-reviewer.notify.wecom.enabled=true",
    "code-reviewer.notify.wecom.webhook-url=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test",
    "code-reviewer.notify.wecom.secret=wecom-secret"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private GitLabProperties gitLabProperties;

    @Autowired
    private LlmProviderProperties llmProviderProperties;

    @Autowired
    private WeComProperties weComProperties;

    @TestConfiguration
    @EnableConfigurationProperties({
        AppProperties.class,
        GitLabProperties.class,
        LlmProviderProperties.class,
        WeComProperties.class
    })
    static class TestConfig {
    }

    @Test
    void shouldBindConfigurationProperties() {
        assertEquals(2, appProperties.getAsync().getCorePoolSize());
        assertEquals(6, appProperties.getAsync().getMaxPoolSize());
        assertEquals("test-review-", appProperties.getAsync().getThreadNamePrefix());

        assertEquals("https://gitlab.example.com", gitLabProperties.getUrl());
        assertEquals("gitlab-access-token", gitLabProperties.getToken());
        assertEquals(12000, gitLabProperties.getReadTimeoutMs());

        assertEquals("https://api.example.com", llmProviderProperties.getBaseUrl());
        assertEquals("deepseek-chat", llmProviderProperties.getModel());
        assertEquals(2048, llmProviderProperties.getMaxTokens());

        assertTrue(weComProperties.isEnabled());
        assertEquals("wecom-secret", weComProperties.getSecret());
    }
}