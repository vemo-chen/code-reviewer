package com.vemo.codereview.review.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCodeSnippet {

    private String title;
    private Integer startLine;
    private Integer endLine;
    private String content;
}
