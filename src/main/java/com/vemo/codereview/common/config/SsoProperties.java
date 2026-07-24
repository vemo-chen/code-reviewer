package com.vemo.codereview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code-reviewer.sso")
public class SsoProperties {

    private String baseUrl = "";
    private String loginPath = "";
    private String employeeInfoPath = "";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getLoginPath() {
        return loginPath;
    }

    public void setLoginPath(String loginPath) {
        this.loginPath = loginPath;
    }

    public String getEmployeeInfoPath() {
        return employeeInfoPath;
    }

    public void setEmployeeInfoPath(String employeeInfoPath) {
        this.employeeInfoPath = employeeInfoPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

}
