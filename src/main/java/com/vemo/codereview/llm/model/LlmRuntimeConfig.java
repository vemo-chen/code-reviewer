package com.vemo.codereview.llm.model;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmRuntimeConfig {

    private Long modelConfigId;
    private String providerCode;
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer timeoutMs;
    private Integer maxTokens;
    private BigDecimal temperature;
    private boolean fallbackConfig;
}
