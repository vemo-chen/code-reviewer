package com.vemo.codereview.review.service;

import com.vemo.codereview.common.config.ReviewRuleProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewRuleService {

    private static final String DEFAULT_RULES =
        "# Review Goals\n"
            + "- Review only the code included in the current change.\n"
            + "- Focus on actionable, reproducible review comments.\n"
            + "\n"
            + "# High Priority Issues\n"
            + "- Logic bugs, null pointer risks, boundary conditions, and missing exception handling.\n"
            + "- Injection risks, sensitive data leaks, unsafe deserialization, and other security issues.\n"
            + "- Resource leaks, concurrency risks, and obvious performance regressions.\n"
            + "\n"
            + "# Maintainability\n"
            + "- Readability, naming, duplication, complex branches, and unclear responsibilities.\n"
            + "- Missing validation, logging, or test coverage.\n"
            + "\n"
            + "# Output Requirements\n"
            + "- Return strict JSON only.\n"
            + "- Do not invent file paths or line numbers.\n"
            + "- Keep summaries concise, specific, and actionable.\n";

    private static final String DEFAULT_PROMPT =
        "Prioritize correctness, security, maintainability, and performance risks in this change. "
            + "Return concrete, actionable fixes and clearly identify any project hard-rule violations.";

    private final ReviewRuleProperties reviewRuleProperties;

    private volatile String activeRules = DEFAULT_RULES;
    private volatile long lastModified = -1L;

    public ReviewRuleService(ReviewRuleProperties reviewRuleProperties) {
        this.reviewRuleProperties = reviewRuleProperties;
    }

    @PostConstruct
    public void init() {
        refreshRulesIfNeeded();
    }

    @Scheduled(fixedDelayString = "${code-reviewer.review.rule-refresh-ms:30000}")
    public void refreshRulesIfNeeded() {
        Path path = resolveLocalRulePath();
        try {
            if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
                activeRules = DEFAULT_RULES;
                lastModified = -1L;
                return;
            }

            long currentLastModified = Files.getLastModifiedTime(path).toMillis();
            if (currentLastModified == lastModified && StringUtils.hasText(activeRules)) {
                return;
            }

            String content =
                stripUtf8Bom(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).trim();
            activeRules = StringUtils.hasText(content) ? content : DEFAULT_RULES;
            lastModified = currentLastModified;
        } catch (IOException ex) {
            activeRules = DEFAULT_RULES;
            lastModified = -1L;
        }
    }

    public String getActiveRulesText() {
        return activeRules;
    }

    public String getDefaultPromptText() {
        return DEFAULT_PROMPT;
    }

    Path resolveLocalRulePath() {
        String configuredPath = reviewRuleProperties.getRulesFile();
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }

        Path directPath = Paths.get(configuredPath);
        if (directPath.isAbsolute()) {
            return directPath.normalize();
        }

        Path workingDirPath = Paths.get("").toAbsolutePath().resolve(configuredPath).normalize();
        if (Files.exists(workingDirPath)) {
            return workingDirPath;
        }

        return directPath.toAbsolutePath().normalize();
    }

    private String stripUtf8Bom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }
}
