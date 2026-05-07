package com.vemo.codereview.review.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewContextStats {

    private int changedFileCount;
    private int enrichedFileCount;
    private int skippedFileCount;
    private int totalContextChars;
}
