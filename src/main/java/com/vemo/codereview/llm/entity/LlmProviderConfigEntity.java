package com.vemo.codereview.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("llm_model_config")
@Getter
@Setter
public class LlmProviderConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
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
    private String remark;
    private Date createdAt;
    private Date updatedAt;
}
