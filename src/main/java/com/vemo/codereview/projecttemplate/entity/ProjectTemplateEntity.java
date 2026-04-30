package com.vemo.codereview.projecttemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("project_template")
@Getter
@Setter
public class ProjectTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String templateDesc;
    private String fileExtensions;
    private String baseReviewPrompt;
    private Long createdBy;
    private Long updatedBy;
    private Date createdAt;
    private Date updatedAt;
}
