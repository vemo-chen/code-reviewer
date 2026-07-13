package com.vemo.codereview.review.model;

import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MrReviewCompletion {
    private boolean completed;
    private CodeReviewResultEntity result;
}
