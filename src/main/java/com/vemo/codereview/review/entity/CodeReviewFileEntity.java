package com.vemo.codereview.review.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("code_review_file")
@Getter
@Setter
public class CodeReviewFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String filePath;
    private String changeType;
    private String diffExcerpt;
    private Boolean isSkipped;
    private String skipReason;
    private Date createdAt;

}
