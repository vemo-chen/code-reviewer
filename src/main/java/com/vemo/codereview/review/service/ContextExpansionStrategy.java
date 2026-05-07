package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.List;

public interface ContextExpansionStrategy {

    boolean supports(String filePath);

    List<ReviewCodeSnippet> expand(
        GitLabChangesPayload.Change change,
        String fileContent,
        List<Integer> changedLines,
        ReviewContextRisk risk
    );
}
