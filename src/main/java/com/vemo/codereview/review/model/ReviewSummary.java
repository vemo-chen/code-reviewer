package com.vemo.codereview.review.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSummary {

    private Integer suggestedScore;
    private Integer deductionScore;
    private Integer finalScore;
    private String summary;
    private String briefSummary;
    private String riskLevel;
    private String scoreReason;
    private String advice;
    private List<ReviewCommentDraft> comments;
}
