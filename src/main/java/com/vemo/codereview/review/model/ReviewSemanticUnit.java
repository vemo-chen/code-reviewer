package com.vemo.codereview.review.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSemanticUnit {
    private String unitKey;
    private String filePath;
    private String changeType;
    private String semanticType;
    private Integer startLine;
    private Integer endLine;
    private String diff;
    private String ref;
    private String expandedCode;
    private List<String> riskHints;
    private Boolean truncated;
}
