package com.vemo.codereview.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("project_profile")
@Getter
@Setter
public class ProjectProfileEntity {

    @TableId(type = IdType.AUTO)
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
    private String supportedFileExtensions;
    private Long llmModelId;
    private Boolean aiReviewEnabled;
    private Boolean reviewContextEnabled;
    private Boolean gitlabNoteEnabled;
    private Boolean wecomNotifyEnabled;
    private String wecomWebhookUrl;
    private String promptContent;
    private Boolean active;
    private Date createdAt;
    private Date updatedAt;
}
