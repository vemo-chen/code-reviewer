package com.vemo.codereview.project.model;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectDetailResponse {

    private Long id;
    private String projectKey;
    private String projectName;
    private String sourcePlatform;
    private Long gitlabProjectId;
    private String gitlabProjectUrl;
    private String gitlabWebhookToken;
    private String reviewBranches;
    private Long ownerUserId;
    private Long templateId;
    private String templateName;
    private String supportedFileExtensions;
    private Long llmModelId;
    private Boolean aiReviewEnabled;
    private Boolean gitlabNoteEnabled;
    private Boolean wecomNotifyEnabled;
    private String wecomWebhookUrl;
    private String promptContent;
    private Boolean active;
    private Date createdAt;
    private Date updatedAt;
}
