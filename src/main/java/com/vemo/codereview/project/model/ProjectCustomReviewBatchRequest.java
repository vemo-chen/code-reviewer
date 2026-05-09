package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProjectCustomReviewBatchRequest {

    private String startTime;
    private String endTime;
    private String reviewMode;
    private List<String> reviewBranches;
}
