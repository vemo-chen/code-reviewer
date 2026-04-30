package com.vemo.codereview.projecttemplate.model;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTemplateDetailResponse {

    private Long id;
    private String templateName;
    private String templateDesc;
    private String fileExtensions;
    private String baseReviewPrompt;
    private Long createdBy;
    private boolean manageable;
    private Date createdAt;
    private Date updatedAt;
}
