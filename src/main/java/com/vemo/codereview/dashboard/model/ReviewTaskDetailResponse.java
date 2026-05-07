package com.vemo.codereview.dashboard.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewTaskDetailResponse {

    private Long taskId;
    private Long projectId;
    private String projectName;
    private String targetId;
    private String targetTitle;
    private String submitBranch;
    private String status;
    private Long ownerUserId;
    private String ownerDisplayName;
    private Integer retryCount;
    private String operatorName;
    private Date createdAt;
    private Date finishedAt;
    private String riskLevel;
    private Integer suggestedScore;
    private Integer deductionScore;
    private Integer finalScore;
    private String scoreReason;
    private String summary;
    private String briefSummary;
    private String fixStatus;
    private Long fixSubmittedBy;
    private String fixSubmittedByName;
    private Date fixSubmittedAt;
    private Long fixReviewedBy;
    private String fixReviewedByName;
    private Date fixReviewedAt;
    private String fixReviewComment;
    private List<CommentItem> comments = new ArrayList<CommentItem>();

    @Getter
    @Setter
    public static class CommentItem {
        private Long id;
        private String filePath;
        private Integer lineNo;
        private String severity;
        private String category;
        private String message;
        private String suggestion;
        private String currentCode;
        private String suggestedCode;
        private Integer codeStartLine;
        private Integer codeEndLine;
        private String evidenceType;
        private String confidence;
        private Boolean isPosted;
        private Date createdAt;
    }
}
