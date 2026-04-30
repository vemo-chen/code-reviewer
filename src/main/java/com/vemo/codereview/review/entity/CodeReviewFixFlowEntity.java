package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_fix_flow")
@Getter
@Setter
public class CodeReviewFixFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String operatorName;
    private String comment;
    private Date createdAt;
}
