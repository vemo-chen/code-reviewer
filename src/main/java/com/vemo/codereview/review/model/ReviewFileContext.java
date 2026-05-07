package com.vemo.codereview.review.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewFileContext {

    private String filePath;
    private String ref;
    private String language;
    private String changeType;
    private String contentStatus;
    private String skipReason;
    private List<ReviewCodeSnippet> snippets;
    private List<String> riskHints;
    private Boolean truncated;
}
