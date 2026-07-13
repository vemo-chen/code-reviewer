package com.vemo.codereview.review.service;

import com.vemo.codereview.common.config.AppProperties;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewFileContext;
import java.util.ArrayList;
import java.util.List;
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

    public int maxTotalChars() {
        return appProperties.getReviewContext().getMaxTotalChars();
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

    public List<ReviewFileContext> applyTotalBudget(List<ReviewFileContext> contexts) {
        List<ReviewFileContext> result = new ArrayList<ReviewFileContext>();
        int total = 0;
        for (ReviewFileContext context : contexts) {
            int size = chars(context);
            if (total + size > maxTotalChars()) {
                context.setTruncated(Boolean.TRUE);
                break;
            }
            result.add(context);
            total += size;
        }
        return result;
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
