package com.vemo.codereview.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    "code-reviewer.app.review-worker.enabled=false",
    "code-reviewer.app.review-worker.worker-count=3",
    "code-reviewer.app.review-worker.idle-sleep-ms=2000",
    "code-reviewer.app.review-worker.error-sleep-ms=4000",
    "code-reviewer.app.review-context.max-request-body-bytes=2097152",
    "code-reviewer.app.platform-url=http://10.12.8.132:5173/reviews",
    "code-reviewer.gitlab.connect-timeout-ms=7000",
    "code-reviewer.gitlab.read-timeout-ms=12000"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private GitLabProperties gitLabProperties;

    @TestConfiguration
    @EnableConfigurationProperties({
        AppProperties.class,
        GitLabProperties.class
    })
    static class TestConfig {
    }

    @Test
    void shouldBindConfigurationProperties() {
        assertEquals(2, appProperties.getAsync().getCorePoolSize());
        assertEquals(6, appProperties.getAsync().getMaxPoolSize());
        assertEquals("test-review-", appProperties.getAsync().getThreadNamePrefix());
        assertFalse(appProperties.getReviewWorker().isEnabled());
        assertEquals(3, appProperties.getReviewWorker().getWorkerCount());
        assertEquals(2000L, appProperties.getReviewWorker().getIdleSleepMs());
        assertEquals(4000L, appProperties.getReviewWorker().getErrorSleepMs());
        assertEquals(2097152, appProperties.getReviewContext().getMaxRequestBodyBytes());
        assertEquals("http://10.12.8.132:5173/reviews", appProperties.getPlatformUrl());

        assertEquals(7000, gitLabProperties.getConnectTimeoutMs());
        assertEquals(12000, gitLabProperties.getReadTimeoutMs());
    }

    @Test
    void shouldProvideDefaultReviewContextProperties() {
        AppProperties defaults = new AppProperties();
        assertEquals(20, defaults.getReviewContext().getMaxFiles());
        assertEquals(204800, defaults.getReviewContext().getMaxFileBytes());
        assertEquals(4194304, defaults.getReviewContext().getMaxRequestBodyBytes());
        assertEquals(30, defaults.getReviewContext().getDefaultContextLines());
        assertEquals(60, defaults.getReviewContext().getHighRiskContextLines());
        assertEquals(5, defaults.getReviewContext().getMaxSnippetsPerFile());
    }
}
