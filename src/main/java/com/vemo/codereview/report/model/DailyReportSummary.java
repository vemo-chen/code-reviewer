package com.vemo.codereview.report.model;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
public class DailyReportSummary {

    private Date reportDate;
    private Long projectId;
    private String projectName;
    private String developerId;
    private String developerName;
    private int commitCount;
    private int mrCount;
    private int reviewCount;
    private int highRiskCount;
    private String summary;
}
