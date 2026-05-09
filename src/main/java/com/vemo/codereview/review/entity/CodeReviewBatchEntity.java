package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_batch")
@Getter
@Setter
public class CodeReviewBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String triggerType;
    private String reviewMode;
    private Date startTime;
    private Date endTime;
    private String branchScope;
    private String status;
    private Long createdBy;
    private String createdByName;
    private Integer totalCommitCount;
    private Integer createdTaskCount;
    private Integer retriedTaskCount;
    private Integer skippedReviewedCount;
    private Integer skippedRunningCount;
    private Integer skippedFailedCount;
    private Integer failedCount;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;
    private Date finishedAt;
}
