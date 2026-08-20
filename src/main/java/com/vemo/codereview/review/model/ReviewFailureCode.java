package com.vemo.codereview.review.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

public enum ReviewFailureCode {

    LLM_API_ERROR("LLM_API_ERROR", "模型服务返回错误响应"),
    LLM_IO_ERROR("LLM_IO_ERROR", "模型服务连接失败或超时"),
    LLM_EMPTY_RESPONSE("LLM_EMPTY_RESPONSE", "模型服务返回空内容"),
    LLM_REQUEST_SERIALIZATION_ERROR("LLM_REQUEST_SERIALIZATION_ERROR", "模型请求序列化失败"),
    LLM_EMPTY_RESPONSE_BODY("LLM_EMPTY_RESPONSE_BODY", "模型服务返回空内容"),
    LLM_PROVIDER_UNSUPPORTED("LLM_PROVIDER_UNSUPPORTED", "当前模型提供方暂不支持"),
    LLM_UNKNOWN("LLM_UNKNOWN", "模型服务调用失败"),
    GITLAB_API_ERROR("GITLAB_API_ERROR", "GitLab 接口返回错误响应"),
    GITLAB_EMPTY_RESPONSE("GITLAB_EMPTY_RESPONSE", "GitLab 接口返回空内容"),
    GITLAB_RESPONSE_PARSE_ERROR("GITLAB_RESPONSE_PARSE_ERROR", "GitLab 响应解析失败"),
    GITLAB_REQUEST_BUILD_ERROR("GITLAB_REQUEST_BUILD_ERROR", "GitLab 请求构建失败"),
    GITLAB_COMPARE_INCOMPLETE("GITLAB_COMPARE_INCOMPLETE", "GitLab 对比结果不完整"),
    GITLAB_UNKNOWN("GITLAB_UNKNOWN", "GitLab 调用失败"),
    REVIEW_RESULT_EMPTY("REVIEW_RESULT_EMPTY", "模型返回内容为空"),
    REVIEW_RESULT_TRUNCATED("REVIEW_RESULT_TRUNCATED", "模型输出被截断"),
    REVIEW_REQUEST_BODY_TOO_LARGE("REVIEW_REQUEST_BODY_TOO_LARGE", "审查请求内容过大"),
    REVIEW_SINGLE_UNIT_REQUEST_TOO_LARGE("REVIEW_SINGLE_UNIT_REQUEST_TOO_LARGE", "单个审查单元内容过大"),
    REVIEW_SINGLE_UNIT_OUTPUT_TRUNCATED("REVIEW_SINGLE_UNIT_OUTPUT_TRUNCATED", "单个审查单元输出被截断"),
    REVIEW_HASH_ERROR("REVIEW_HASH_ERROR", "生成评论标识失败"),
    REVIEW_UNKNOWN("REVIEW_UNKNOWN", "审查结果处理失败"),
    TASK_NOT_FOUND("TASK_NOT_FOUND", "审查任务不存在"),
    TASK_STATE_INVALID("TASK_STATE_INVALID", "审查任务状态异常"),
    TASK_RETRY_INVALID("TASK_RETRY_INVALID", "当前任务状态不允许重试"),
    TASK_INTERRUPT_INVALID("TASK_INTERRUPT_INVALID", "当前任务状态不允许停止"),
    TASK_TYPE_UNSUPPORTED("TASK_TYPE_UNSUPPORTED", "当前任务类型不支持审查"),
    TASK_ACCESS_DENIED("TASK_ACCESS_DENIED", "没有审查任务访问权限"),
    TASK_UNKNOWN("TASK_UNKNOWN", "审查任务处理失败"),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在"),
    PROJECT_URL_REQUIRED("PROJECT_URL_REQUIRED", "项目地址缺失"),
    PROJECT_URL_INVALID("PROJECT_URL_INVALID", "项目地址无效"),
    PROJECT_ACCESS_DENIED("PROJECT_ACCESS_DENIED", "没有项目访问权限"),
    PROJECT_OWNER_REQUIRED("PROJECT_OWNER_REQUIRED", "项目负责人缺失"),
    PROJECT_MEMBER_REQUIRED("PROJECT_MEMBER_REQUIRED", "项目成员缺失"),
    PROJECT_TEMPLATE_NOT_FOUND("PROJECT_TEMPLATE_NOT_FOUND", "项目模板不存在"),
    PROJECT_UNKNOWN("PROJECT_UNKNOWN", "项目配置失败"),
    WECOM_WEBHOOK_MISSING("WECOM_WEBHOOK_MISSING", "企业微信通知未配置"),
    WECOM_PUSH_ERROR("WECOM_PUSH_ERROR", "企业微信通知发送失败"),
    WECOM_PUSH_IO_ERROR("WECOM_PUSH_IO_ERROR", "企业微信通知连接失败或超时"),
    WECOM_PUSH_INTERRUPTED("WECOM_PUSH_INTERRUPTED", "企业微信通知被中断"),
    WECOM_UNKNOWN("WECOM_UNKNOWN", "企业微信通知失败"),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未登录或登录已失效"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "当前账号无权限"),
    AUTH_INVALID("AUTH_INVALID", "认证信息无效"),
    AUTH_UNKNOWN("AUTH_UNKNOWN", "认证失败"),
    UNKNOWN("UNKNOWN", "审查执行失败");

    private static final Map<String, ReviewFailureCode> LOOKUP;

    static {
        Map<String, ReviewFailureCode> lookup = new HashMap<String, ReviewFailureCode>();
        for (ReviewFailureCode value : values()) {
            lookup.put(value.code, value);
        }
        LOOKUP = Collections.unmodifiableMap(lookup);
    }

    private final String code;
    private final String userMessage;

    ReviewFailureCode(String code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public static ReviewFailureCode fromCode(String code) {
        if (!StringUtils.hasText(code)) {
            return UNKNOWN;
        }
        ReviewFailureCode direct = LOOKUP.get(code.trim().toUpperCase());
        if (direct != null) {
            return direct;
        }
        String normalized = code.trim().toUpperCase();
        if (normalized.startsWith("LLM_")) {
            return LLM_UNKNOWN;
        }
        if (normalized.startsWith("GITLAB_")) {
            return GITLAB_UNKNOWN;
        }
        if (normalized.startsWith("REVIEW_")) {
            return REVIEW_UNKNOWN;
        }
        if (normalized.startsWith("TASK_")) {
            return TASK_UNKNOWN;
        }
        if (normalized.startsWith("PROJECT_")) {
            return PROJECT_UNKNOWN;
        }
        if (normalized.startsWith("WECOM_")) {
            return WECOM_UNKNOWN;
        }
        if (normalized.startsWith("AUTH_")) {
            return AUTH_UNKNOWN;
        }
        return UNKNOWN;
    }
}
