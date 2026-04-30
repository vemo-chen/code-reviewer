package com.vemo.codereview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code-reviewer.review")
public class ReviewRuleProperties {

    private String rulesFile = "config/review-rules.md";
    private long ruleRefreshMs = 30000L;

    public String getRulesFile() {
        return rulesFile;
    }

    public void setRulesFile(String rulesFile) {
        this.rulesFile = rulesFile;
    }

    public long getRuleRefreshMs() {
        return ruleRefreshMs;
    }

    public void setRuleRefreshMs(long ruleRefreshMs) {
        this.ruleRefreshMs = ruleRefreshMs;
    }
}
