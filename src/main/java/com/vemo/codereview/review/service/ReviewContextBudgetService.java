package com.vemo.codereview.review.service;

import com.vemo.codereview.common.config.AppProperties;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewFileContext;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class ReviewContextBudgetService {

    private final AppProperties appProperties;

    public ReviewContextBudgetService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public int maxFiles() {
        return appProperties.getReviewContext().getMaxFiles();
    }

    public int maxFileBytes() {
        return appProperties.getReviewContext().getMaxFileBytes();
    }

    public int maxRequestBodyBytes() {
        return appProperties.getReviewContext().getMaxRequestBodyBytes();
    }

    public int maxSnippetsPerFile() {
        return appProperties.getReviewContext().getMaxSnippetsPerFile();
    }

    public void applyFileBudget(ReviewFileContext fileContext) {
        if (fileContext == null || fileContext.getSnippets() == null) {
            return;
        }
        int maxSnippets = appProperties.getReviewContext().getMaxSnippetsPerFile();
        if (fileContext.getSnippets().size() > maxSnippets) {
            fileContext.setSnippets(new ArrayList<ReviewCodeSnippet>(fileContext.getSnippets().subList(0, maxSnippets)));
            fileContext.setTruncated(Boolean.TRUE);
        }
    }

    public int chars(ReviewFileContext context) {
        if (context == null || context.getSnippets() == null) {
            return 0;
        }
        int total = 0;
        for (ReviewCodeSnippet snippet : context.getSnippets()) {
            total += snippet.getContent() == null ? 0 : snippet.getContent().length();
        }
        return total;
    }
}
