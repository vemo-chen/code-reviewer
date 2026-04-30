package com.vemo.codereview.review.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("code_review_comment")
@Getter
@Setter
public class CodeReviewCommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private String filePath;
    private Integer lineNo;
    private String severity;
    private String category;
    private String message;
    private String suggestion;
    private String commentHash;
    private Boolean isPosted;
    private Date postedAt;
    private Date createdAt;

}
