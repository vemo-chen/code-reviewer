package com.vemo.codereview.dashboard.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewTaskQueryRequest {

    private long pageNo = 1;
    private long pageSize = 10;
    private Long projectId;
    private String status;
    private String fixStatus;
    private String riskLevel;
    private String gitlabUsername;
    private String operatorName;
    private String targetTitle;
    private String startDate;
    private String endDate;
    private String sortField;
    private String sortOrder;
}
