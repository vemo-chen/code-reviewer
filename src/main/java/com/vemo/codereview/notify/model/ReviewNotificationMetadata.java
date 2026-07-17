package com.vemo.codereview.notify.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewNotificationMetadata {

    private String reviewTargetType;
    private String targetId;
    private String submitMessage;
    private String submitter;
    private String submitBranch;
    private String submitTime;
    private String pushBranch;
    private String beforeSha;
    private String afterSha;
    private Integer commitCount;
    private String gitlabUrl;
}
