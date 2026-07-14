package com.vemo.codereview.review.model;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import java.util.List;
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
    private String sourceRef;
    private String targetRef;
    private String headSha;
    private String pushBranch;
    private String beforeSha;
    private String afterSha;
    private Integer commitCount;
    private GitLabChangesPayload mergeRequestChanges;
    private List<ReviewFileContext> fileContexts;
    private ReviewContextStats contextStats;
}
