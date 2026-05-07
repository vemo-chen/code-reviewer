package com.vemo.codereview.review.model;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class ReviewCommentDraft {

    private String filePath;
    private Integer line;
    private String severity;
    private String category;
    private String message;
    private String suggestion;
    private String suggestedCode;
    private Integer codeStartLine;
    private Integer codeEndLine;
    private String evidenceType;
    private String confidence;
    private String commentHash;
}
