package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_result")
@Getter
@Setter
public class CodeReviewResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String providerName;
    private String modelName;
    private String riskLevel;
    private Integer suggestedScore;
    private Integer deductionScore;
    private Integer finalScore;
    private String summary;
    private String briefSummary;
    private String scoreReason;
    private String advice;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
    private String rawResponse;
    private String wecomNotifyStatus;
    private Integer wecomNotifyAttempts;
    private Date wecomNotifiedAt;
    private String wecomNotifyErrorCode;
    private String wecomNotifyErrorMessage;
    private Date createdAt;
}
