package com.vemo.codereview.review.service;

import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.platform.gitlab.service.GitLabReviewTargetService;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import com.vemo.codereview.review.model.ReviewContextStats;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFileContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class ReviewContextEnrichmentService {

    private final ChangeReviewFilter changeReviewFilter;
    private final GitLabReviewTargetService gitLabReviewTargetService;
    private final ReviewRefResolver reviewRefResolver;
    private final DiffHunkParser diffHunkParser;
    private final ReviewContextRiskAnalyzer riskAnalyzer;
    private final ReviewContextBudgetService budgetService;
    private final FallbackLineWindowExpansionStrategy fallbackStrategy;
    private final List<ContextExpansionStrategy> strategies;

    public ReviewContextEnrichmentService(
        ChangeReviewFilter changeReviewFilter,
        GitLabReviewTargetService gitLabReviewTargetService,
        ReviewRefResolver reviewRefResolver,
        DiffHunkParser diffHunkParser,
        ReviewContextRiskAnalyzer riskAnalyzer,
        ReviewContextBudgetService budgetService,
        FallbackLineWindowExpansionStrategy fallbackStrategy,
        List<ContextExpansionStrategy> strategies) {
        this.changeReviewFilter = changeReviewFilter;
        this.gitLabReviewTargetService = gitLabReviewTargetService;
        this.reviewRefResolver = reviewRefResolver;
        this.diffHunkParser = diffHunkParser;
        this.riskAnalyzer = riskAnalyzer;
        this.budgetService = budgetService;
        this.fallbackStrategy = fallbackStrategy;
        this.strategies = strategies;
    }

    public void enrich(
        ReviewExecutionContext context,
        ProjectProfileEntity projectConfig,
        Long gitLabProjectId,
        String gitLabProjectUrl,
        String gitLabApiToken) {
        if (context == null || projectConfig == null || Boolean.FALSE.equals(projectConfig.getReviewContextEnabled())) {
            return;
        }
        reviewRefResolver.resolve(context);
        if (!StringUtils.hasText(context.getSourceRef())) {
            return;
        }

        List<GitLabChangesPayload.Change> changes = context.getMergeRequestChanges() == null
            ? Collections.<GitLabChangesPayload.Change>emptyList()
            : context.getMergeRequestChanges().getChanges();
        List<GitLabChangesPayload.Change> reviewableChanges = changeReviewFilter.filterReviewableChanges(
            changes,
            context.getSupportedFileExtensions()
        );

        List<ReviewFileContext> fileContexts = new ArrayList<ReviewFileContext>();
        int processed = 0;
        for (GitLabChangesPayload.Change change : reviewableChanges) {
            if (processed >= budgetService.maxFiles()) {
                break;
            }
            fileContexts.add(enrichFile(change, context.getSourceRef(), gitLabProjectId, gitLabProjectUrl, gitLabApiToken));
            processed++;
        }
        fileContexts = budgetService.applyTotalBudget(fileContexts);
        context.setFileContexts(fileContexts);
        context.setContextStats(stats(changes.size(), fileContexts));
    }

    private ReviewFileContext enrichFile(
        GitLabChangesPayload.Change change,
        String ref,
        Long gitLabProjectId,
        String gitLabProjectUrl,
        String gitLabApiToken) {
        ReviewFileContext fileContext = new ReviewFileContext();
        String filePath = change.getNewPath() != null ? change.getNewPath() : change.getOldPath();
        fileContext.setFilePath(filePath);
        fileContext.setRef(ref);
        fileContext.setChangeType(resolveChangeType(change));
        fileContext.setLanguage(resolveLanguage(filePath));

        if (Boolean.TRUE.equals(change.getDeletedFile())) {
            fileContext.setContentStatus("SKIPPED");
            fileContext.setSkipReason("deleted file");
            return fileContext;
        }

        ReviewContextRisk risk = riskAnalyzer.analyze(filePath, change.getDiff());
        fileContext.setRiskHints(risk.getHints());
        try {
            String content = gitLabReviewTargetService.getRepositoryFileRaw(gitLabProjectUrl, gitLabProjectId, filePath, ref, gitLabApiToken);
            if (content != null && content.length() > budgetService.maxFileBytes()) {
                fileContext.setContentStatus("TOO_LARGE");
                fileContext.setSkipReason("file is too large");
                return fileContext;
            }
            List<Integer> changedLines = diffHunkParser.parseChangedNewLines(change.getDiff());
            List<ReviewCodeSnippet> snippets = expand(change, content, changedLines, risk);
            if (CollectionUtils.isEmpty(snippets)) {
                snippets = fallbackStrategy.expand(change, content, changedLines, risk);
            }
            fileContext.setSnippets(snippets);
            fileContext.setContentStatus(CollectionUtils.isEmpty(snippets) ? "SKIPPED" : "FETCHED");
            budgetService.applyFileBudget(fileContext);
            return fileContext;
        } catch (RuntimeException ex) {
            fileContext.setContentStatus("ERROR");
            fileContext.setSkipReason(ex.getMessage());
            return fileContext;
        }
    }

    private List<ReviewCodeSnippet> expand(
        GitLabChangesPayload.Change change,
        String content,
        List<Integer> changedLines,
        ReviewContextRisk risk) {
        String filePath = change.getNewPath() != null ? change.getNewPath() : change.getOldPath();
        for (ContextExpansionStrategy strategy : strategies) {
            if (strategy == fallbackStrategy) {
                continue;
            }
            if (strategy.supports(filePath)) {
                List<ReviewCodeSnippet> snippets = strategy.expand(change, content, changedLines, risk);
                if (!CollectionUtils.isEmpty(snippets)) {
                    return snippets;
                }
            }
        }
        return Collections.emptyList();
    }

    private ReviewContextStats stats(int changedFileCount, List<ReviewFileContext> fileContexts) {
        ReviewContextStats stats = new ReviewContextStats();
        stats.setChangedFileCount(changedFileCount);
        stats.setEnrichedFileCount(0);
        stats.setSkippedFileCount(0);
        int chars = 0;
        for (ReviewFileContext fileContext : fileContexts) {
            if ("FETCHED".equals(fileContext.getContentStatus())) {
                stats.setEnrichedFileCount(stats.getEnrichedFileCount() + 1);
                chars += budgetService.chars(fileContext);
            } else {
                stats.setSkippedFileCount(stats.getSkippedFileCount() + 1);
            }
        }
        stats.setTotalContextChars(chars);
        return stats;
    }

    private String resolveChangeType(GitLabChangesPayload.Change change) {
        if (Boolean.TRUE.equals(change.getNewFile())) {
            return "ADDED";
        }
        if (Boolean.TRUE.equals(change.getDeletedFile())) {
            return "DELETED";
        }
        if (Boolean.TRUE.equals(change.getRenamedFile())) {
            return "RENAMED";
        }
        return "MODIFIED";
    }

    private String resolveLanguage(String filePath) {
        if (filePath == null) {
            return "unknown";
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".vue") || lower.endsWith(".ts") || lower.endsWith(".tsx")
            || lower.endsWith(".js") || lower.endsWith(".jsx")) return "frontend";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx") || lower.endsWith(".c")
            || lower.endsWith(".hpp") || lower.endsWith(".hh") || lower.endsWith(".hxx") || lower.endsWith(".h")) return "cpp";
        return "text";
    }
}
