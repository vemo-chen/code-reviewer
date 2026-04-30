package com.vemo.codereview.llm.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmModelQueryRequest {

    private long pageNo;
    private long pageSize;
    private String configName;
    private String providerCode;
    private String scopeType;
    private Boolean enabled;
}
