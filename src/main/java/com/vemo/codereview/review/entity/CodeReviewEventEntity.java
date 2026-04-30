package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_event")
@Getter
@Setter
public class CodeReviewEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourcePlatform;
    private String eventType;
    private Long projectId;
    private String projectName;
    private String objectId;
    private String objectType;
    private String operatorId;
    private String operatorName;
    private String submitBranch;
    private String idempotentKey;
    private String payloadJson;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
