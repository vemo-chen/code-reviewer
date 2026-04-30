package com.vemo.codereview.llm.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectLlmModelOptionResponse {

    private Long id;
    private String configName;
    private String providerCode;
    private String modelName;
    private String scopeType;
}
