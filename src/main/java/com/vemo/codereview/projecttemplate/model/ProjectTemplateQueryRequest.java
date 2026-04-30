package com.vemo.codereview.projecttemplate.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTemplateQueryRequest {

    private long pageNo = 1;
    private long pageSize = 10;
    private String templateName;
}
