package com.vemo.codereview.llm.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmModelUpsertRequest {

    private String configName;
    private String providerCode;
    private String providerName;
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Boolean enabled;
    private String scopeType;
    private Long maintainerProjectId;
    private Integer timeoutMs;
    private Integer maxTokens;
    private BigDecimal temperature;
    private Boolean thinkingEnabled;
    private String remark;
    private List<Long> projectIds;
}
