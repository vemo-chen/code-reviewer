package com.vemo.codereview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code-reviewer.app")
public class AppProperties {

    private Async async = new Async();
    private ReviewContext reviewContext = new ReviewContext();

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    public ReviewContext getReviewContext() {
        return reviewContext;
    }

    public void setReviewContext(ReviewContext reviewContext) {
        this.reviewContext = reviewContext;
    }

    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 200;
        private String threadNamePrefix = "review-worker-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    public static class ReviewContext {
        private int maxFiles = 20;
        private int maxFileBytes = 204800;
        private int maxTotalChars = 60000;
        private int defaultContextLines = 30;
        private int highRiskContextLines = 60;
        private int maxSnippetsPerFile = 5;

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public int getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(int maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getMaxTotalChars() {
            return maxTotalChars;
        }

        public void setMaxTotalChars(int maxTotalChars) {
            this.maxTotalChars = maxTotalChars;
        }

        public int getDefaultContextLines() {
            return defaultContextLines;
        }

        public void setDefaultContextLines(int defaultContextLines) {
            this.defaultContextLines = defaultContextLines;
        }

        public int getHighRiskContextLines() {
            return highRiskContextLines;
        }

        public void setHighRiskContextLines(int highRiskContextLines) {
            this.highRiskContextLines = highRiskContextLines;
        }

        public int getMaxSnippetsPerFile() {
            return maxSnippetsPerFile;
        }

        public void setMaxSnippetsPerFile(int maxSnippetsPerFile) {
            this.maxSnippetsPerFile = maxSnippetsPerFile;
        }
    }
}
