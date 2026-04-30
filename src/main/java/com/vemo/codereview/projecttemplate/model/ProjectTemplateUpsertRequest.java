package com.vemo.codereview.projecttemplate.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTemplateUpsertRequest {

    private String templateName;
    private String templateDesc;
    private String fileExtensions;
    private String baseReviewPrompt;
}
