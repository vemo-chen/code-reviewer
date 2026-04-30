package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectQueryRequest {

    private long pageNo = 1;
    private long pageSize = 10;
    private String projectName;
    private String gitlabProjectUrl;
    private Boolean aiReviewEnabled;
    private Boolean wecomNotifyEnabled;
}
