package com.vemo.codereview.auth.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vemo.codereview.auth.model.SsoEmployeeInfoApiResponse;
import com.vemo.codereview.auth.model.SsoEmployeeProfile;
import com.vemo.codereview.auth.model.SsoLoginApiResponse;
import com.vemo.codereview.common.config.SsoProperties;
import com.vemo.codereview.common.exception.DomainException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SsoAuthClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final SsoProperties ssoProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient;

    @Autowired
    public SsoAuthClient(
        SsoProperties ssoProperties,
        ObjectMapper objectMapper) {
        this(
            ssoProperties,
            objectMapper,
            buildClient(ssoProperties)
        );
    }

    public SsoAuthClient(
        SsoProperties ssoProperties,
        ObjectMapper objectMapper,
        OkHttpClient okHttpClient) {
        this.ssoProperties = ssoProperties;
        this.objectMapper = objectMapper;
        this.okHttpClient = okHttpClient;
    }

    public SsoEmployeeProfile loginAndFetchProfile(String employeeCode, String password) {
        SsoLoginApiResponse.TokenData tokenData = login(employeeCode, password);
        return fetchProfile(tokenData.getAccessToken());
    }

    private SsoLoginApiResponse.TokenData login(String employeeCode, String password) {
        try {
            Map<String, String> payload = new LinkedHashMap<String, String>();
            payload.put("username", employeeCode);
            payload.put("password", password);

            Request request = new Request.Builder()
                .url(buildUrl(ssoProperties.getLoginPath()))
                .header("Content-Type", "application/json")
                .post(RequestBody.create(JSON, objectMapper.writeValueAsString(payload)))
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new DomainException("SSO_LOGIN_FAILED", "公司账号登录失败");
                }
                SsoLoginApiResponse apiResponse = objectMapper.readValue(body, SsoLoginApiResponse.class);
                assertSuccess(apiResponse.getCode(), apiResponse.getMsg(), "SSO_LOGIN_FAILED");
                if (apiResponse.getData() == null || !StringUtils.hasText(apiResponse.getData().getAccessToken())) {
                    throw new DomainException("SSO_LOGIN_FAILED", "公司账号登录未返回访问令牌");
                }
                return apiResponse.getData();
            }
        } catch (DomainException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new DomainException("SSO_LOGIN_FAILED", "公司账号登录失败");
        }
    }

    private SsoEmployeeProfile fetchProfile(String accessToken) {
        try {
            Request request = new Request.Builder()
                .url(buildUrl(ssoProperties.getEmployeeInfoPath()))
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new DomainException("SSO_PROFILE_FAILED", "获取公司账号信息失败");
                }
                SsoEmployeeInfoApiResponse apiResponse = objectMapper.readValue(body, SsoEmployeeInfoApiResponse.class);
                assertSuccess(apiResponse.getCode(), apiResponse.getMsg(), "SSO_PROFILE_FAILED");
                if (apiResponse.getData() == null) {
                    throw new DomainException("SSO_PROFILE_FAILED", "公司账号信息为空");
                }
                SsoEmployeeInfoApiResponse.EmployeeData data = apiResponse.getData();
                SsoEmployeeProfile profile = new SsoEmployeeProfile();
                profile.setSsoUserId(data.getId());
                profile.setEmployeeCode(data.getCode());
                profile.setName(data.getName());
                profile.setEmail(data.getEmail());
                return profile;
            }
        } catch (DomainException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new DomainException("SSO_PROFILE_FAILED", "获取公司账号信息失败");
        }
    }

    private void assertSuccess(String code, String message, String failureCode) {
        if ("SUCCESS".equalsIgnoreCase(code)) {
            return;
        }
        throw new DomainException(failureCode, StringUtils.hasText(message) ? message : "公司账号认证失败");
    }

    private String buildUrl(String path) {
        String baseUrl = ssoProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new DomainException("SSO_CONFIG_INVALID", "SSO服务地址未配置");
        }
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        if (!StringUtils.hasText(path)) {
            throw new DomainException("SSO_CONFIG_INVALID", "SSO服务路径未配置");
        }
        String normalizedPath = path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }

    private static OkHttpClient buildClient(SsoProperties ssoProperties) {
        return new OkHttpClient.Builder()
            .connectTimeout(ssoProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(ssoProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }
}
