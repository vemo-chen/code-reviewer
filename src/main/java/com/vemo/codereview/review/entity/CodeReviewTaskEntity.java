package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_task")
@Getter
@Setter
public class CodeReviewTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String taskType;
    private String sourcePlatform;
    private Long projectId;
    private String targetId;
    private String targetTitle;
    private String status;
    private String fixStatus;
    private Long fixSubmittedBy;
    private Date fixSubmittedAt;
    private Long fixReviewedBy;
    private Date fixReviewedAt;
    private String fixReviewComment;
    private Integer retryCount;
    private Date nextRetryAt;
    private String executionToken;
    private String errorCode;
    private String errorMessage;
    private Date startedAt;
    private Date finishedAt;
    private Date createdAt;
    private Date updatedAt;
}
