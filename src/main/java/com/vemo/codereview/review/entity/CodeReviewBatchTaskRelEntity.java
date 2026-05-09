package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_batch_task_rel")
@Getter
@Setter
public class CodeReviewBatchTaskRelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long taskId;
    private String targetId;
    private String submitBranch;
    private String actionType;
    private String message;
    private Date createdAt;
    private Date updatedAt;
}
