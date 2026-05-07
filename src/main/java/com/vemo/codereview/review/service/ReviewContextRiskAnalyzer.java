package com.vemo.codereview.review.service;

import com.vemo.codereview.common.config.AppProperties;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewContextRiskAnalyzer {

    private final AppProperties appProperties;

    public ReviewContextRiskAnalyzer(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ReviewContextRisk analyze(String filePath, String diff) {
        ReviewContextRisk risk = new ReviewContextRisk();
        AppProperties.ReviewContext config = appProperties.getReviewContext();
        risk.setContextLines(config.getDefaultContextLines());
        risk.setMaxFileChars(config.getMaxFileBytes());

        String normalizedPath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        String normalizedDiff = diff == null ? "" : diff.toLowerCase(Locale.ROOT);
        boolean highRisk = false;
        if (normalizedPath.endsWith("controller.java")) {
            risk.getHints().add("controller file changed");
            highRisk = true;
        }
        if (normalizedPath.endsWith("service.java")) {
            risk.getHints().add("service file changed");
            highRisk = true;
        }
        if (normalizedPath.endsWith("mapper.java") || normalizedPath.endsWith("entity.java")) {
            risk.getHints().add("data model file changed");
            highRisk = true;
        }
        if (normalizedPath.endsWith(".sql") || normalizedPath.endsWith(".yml") || normalizedPath.endsWith(".yaml")
            || normalizedPath.endsWith("pom.xml") || normalizedPath.endsWith("package.json")) {
            risk.getHints().add("configuration or schema file changed");
            highRisk = true;
        }
        if (containsAny(normalizedDiff, "@transactional", "permission", "auth", "token", "password", "cache",
            "redis", "lock", "thread", "async", "bigdecimal", "localdatetime", " delete", " remove", " drop")) {
            risk.getHints().add("sensitive keyword");
            highRisk = true;
        }
        if (StringUtils.hasText(normalizedDiff) && containsAny(normalizedDiff, "+public ", "+protected ")) {
            risk.getHints().add("public api changed");
            highRisk = true;
        }
        if (highRisk) {
            risk.setLevel("HIGH");
            risk.setContextLines(config.getHighRiskContextLines());
        }
        return risk;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
