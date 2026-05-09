package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectCustomReviewBatchResponse {

    private Long batchId;
    private Integer totalCommitCount;
    private Integer createdTaskCount;
    private Integer retriedTaskCount;
    private Integer skippedReviewedCount;
    private Integer skippedRunningCount;
    private Integer skippedFailedCount;
    private Integer failedCount;
}
