package com.vemo.codereview.llm.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmModelTestResponse {

    private String providerType;
    private String modelName;
    private String responseModel;
    private String message;
}
