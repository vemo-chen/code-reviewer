package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.common.config.ReviewRuleProperties;
import com.vemo.codereview.review.service.ReviewRuleService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewRuleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadRulesFromLocalMarkdownFileAndRefresh() throws Exception {
        Path rulesFile = tempDir.resolve("review-rules.md");
        Files.write(rulesFile, "# Rule A\n- First item".getBytes(StandardCharsets.UTF_8));

        ReviewRuleProperties properties = new ReviewRuleProperties();
        properties.setRulesFile(rulesFile.toString());

        ReviewRuleService reviewRuleService = new ReviewRuleService(properties);
        reviewRuleService.init();

        assertTrue(reviewRuleService.getActiveRulesText().contains("First item"));

        Thread.sleep(5L);
        Files.write(rulesFile, "# Rule A\n- Second item".getBytes(StandardCharsets.UTF_8));
        reviewRuleService.refreshRulesIfNeeded();

        assertTrue(reviewRuleService.getActiveRulesText().contains("Second item"));
    }

    @Test
    void shouldFallbackToDefaultRulesWhenLocalFileMissing() {
        ReviewRuleProperties properties = new ReviewRuleProperties();
        properties.setRulesFile(tempDir.resolve("missing-rules.md").toString());

        ReviewRuleService reviewRuleService = new ReviewRuleService(properties);
        reviewRuleService.init();

        assertTrue(reviewRuleService.getActiveRulesText().contains("Review Goals"));
        assertTrue(reviewRuleService.getActiveRulesText().contains("High Priority Issues"));
    }
}
