package com.vemo.codereview.llm.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmModelPageResponse {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<Item> records = new ArrayList<Item>();

    @Getter
    @Setter
    public static class Item {

        private Long id;
        private String configName;
        private String providerCode;
        private String providerName;
        private String providerType;
        private String apiKeyMasked;
        private String modelName;
        private String scopeType;
        private Long maintainerProjectId;
        private String maintainerProjectName;
        private Boolean manageable;
        private Boolean enabled;
        private Date updatedAt;
    }
}
