package com.vemo.codereview.projecttemplate.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTemplatePageResponse {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<ProjectTemplateDetailResponse> records = new ArrayList<ProjectTemplateDetailResponse>();
}
