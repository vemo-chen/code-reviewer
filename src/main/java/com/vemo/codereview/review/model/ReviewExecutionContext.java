package com.vemo.codereview.review.model;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewExecutionContext {

    private Long taskId;
    private Long eventId;
    private Long projectId;
    private String projectPromptContent;
    private String supportedFileExtensions;
    private String targetId;
    private String targetTitle;
    private String targetType;
    private GitLabChangesPayload mergeRequestChanges;
}
