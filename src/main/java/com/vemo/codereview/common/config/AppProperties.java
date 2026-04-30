package com.vemo.codereview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code-reviewer.app")
public class AppProperties {

    private Async async = new Async();

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
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
}
