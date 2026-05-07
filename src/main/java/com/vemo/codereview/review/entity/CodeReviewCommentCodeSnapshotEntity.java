package com.vemo.codereview.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("code_review_comment_code_snapshot")
@Getter
@Setter
public class CodeReviewCommentCodeSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long commentId;
    private String filePath;
    private String ref;
    private String currentCode;
    private String suggestedCode;
    private Integer startLine;
    private Integer endLine;
    private String evidenceType;
    private String confidence;
    private Date createdAt;
}
