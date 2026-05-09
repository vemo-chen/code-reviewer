package com.vemo.codereview.webhook.model;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StandardReviewEvent {

    private String sourcePlatform;
    private String eventType;
    private Long projectId;
    private String projectName;
    private String objectId;
    private String objectType;
    private String operatorId;
    private String operatorName;
    private String submitBranch;
    private Date submitTime;
    private String targetId;
    private String targetTitle;
    private String idempotentKey;
    private String payloadJson;
}
