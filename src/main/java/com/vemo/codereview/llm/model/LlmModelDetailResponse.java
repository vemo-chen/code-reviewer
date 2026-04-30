package com.vemo.codereview.llm.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmModelDetailResponse {

    private Long id;
    private String configName;
    private String providerCode;
    private String providerName;
    private String providerType;
    private String baseUrl;
    private String apiKeyMasked;
    private String modelName;
    private Boolean enabled;
    private String scopeType;
    private Long maintainerProjectId;
    private String maintainerProjectName;
    private Boolean manageable;
    private Integer timeoutMs;
    private Integer maxTokens;
    private BigDecimal temperature;
    private String remark;
    private List<Long> projectIds = new ArrayList<Long>();
    private Date createdAt;
    private Date updatedAt;
}
