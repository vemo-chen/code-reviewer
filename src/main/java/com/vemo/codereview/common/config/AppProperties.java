package com.vemo.codereview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code-reviewer.app")
public class AppProperties {

    private Async async = new Async();
    private ReviewWorker reviewWorker = new ReviewWorker();
    private ReviewContext reviewContext = new ReviewContext();
    private String platformUrl = "http://10.12.8.132:5173/reviews";

    public String getPlatformUrl() {
        return platformUrl;
    }

    public void setPlatformUrl(String platformUrl) {
        this.platformUrl = platformUrl;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    public ReviewWorker getReviewWorker() {
        return reviewWorker;
    }

    public void setReviewWorker(ReviewWorker reviewWorker) {
        this.reviewWorker = reviewWorker;
    }

    public ReviewContext getReviewContext() {
        return reviewContext;
    }

    public void setReviewContext(ReviewContext reviewContext) {
        this.reviewContext = reviewContext;
    }

    public static class Async {
        private int corePoolSize = 8;
        private int maxPoolSize = 16;
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

    public static class ReviewWorker {
        private boolean enabled = true;
        private int workerCount = 8;
        private long idleSleepMs = 5000L;
        private long errorSleepMs = 10000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }

        public long getIdleSleepMs() {
            return idleSleepMs;
        }

        public void setIdleSleepMs(long idleSleepMs) {
            this.idleSleepMs = idleSleepMs;
        }

        public long getErrorSleepMs() {
            return errorSleepMs;
        }

        public void setErrorSleepMs(long errorSleepMs) {
            this.errorSleepMs = errorSleepMs;
        }
    }

    public static class ReviewContext {
        private int maxFiles = 20;
        private int maxFileBytes = 204800;
        private int maxRequestBodyBytes = 4194304;
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

        public int getMaxRequestBodyBytes() {
            return maxRequestBodyBytes;
        }

        public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
            this.maxRequestBodyBytes = maxRequestBodyBytes;
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
