package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewPromptMode;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewFileContext;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromptBuilderService {

    private final ChangeReviewFilter changeReviewFilter;
    private final DiffChunkService diffChunkService;
    private final ReviewRuleService reviewRuleService;

    public PromptBuilderService(
        ChangeReviewFilter changeReviewFilter,
        DiffChunkService diffChunkService,
        ReviewRuleService reviewRuleService) {
        this.changeReviewFilter = changeReviewFilter;
        this.diffChunkService = diffChunkService;
        this.reviewRuleService = reviewRuleService;
    }

    public ReviewPromptPayload build(ReviewExecutionContext reviewContext) {
        List<GitLabChangesPayload.Change> allChanges = reviewContext.getMergeRequestChanges() == null
            ? new ArrayList<GitLabChangesPayload.Change>()
            : reviewContext.getMergeRequestChanges().getChanges();
        List<GitLabChangesPayload.Change> reviewableChanges = changeReviewFilter.filterReviewableChanges(
            allChanges,
            reviewContext.getSupportedFileExtensions()
        );

        List<ReviewPromptPayload.PromptFilePayload> files = new ArrayList<ReviewPromptPayload.PromptFilePayload>();
        Map<String, ReviewFileContext> contextByPath = contextByPath(reviewContext.getFileContexts());
        for (GitLabChangesPayload.Change change : reviewableChanges) {
            ReviewPromptPayload.PromptFilePayload promptFile = new ReviewPromptPayload.PromptFilePayload();
            String filePath = change.getNewPath() != null ? change.getNewPath() : change.getOldPath();
            promptFile.setFilePath(filePath);
            promptFile.setChangeType(resolveChangeType(change));
            promptFile.setDiffChunks(diffChunkService.chunk(change.getDiff()));
            ReviewFileContext fileContext = contextByPath.get(filePath);
            if (fileContext != null) {
                promptFile.setContextStatus(fileContext.getContentStatus());
                promptFile.setSkipReason(fileContext.getSkipReason());
                promptFile.setRiskHints(fileContext.getRiskHints());
                promptFile.setContextSnippets(fileContext.getSnippets());
                promptFile.setContextTruncated(fileContext.getTruncated());
            }
            files.add(promptFile);
        }

        ReviewPromptPayload prompt = new ReviewPromptPayload();
        prompt.setSystemPrompt(buildSystemPrompt());
        prompt.setUserPrompt(buildUserPrompt(reviewContext, allChanges.size(), files));
        prompt.setFiles(files);
        return prompt;
    }

    public ReviewPromptPayload build(ReviewExecutionContext context, ReviewExecutionBatch batch,
                                     int index, int total) {
        return build(context, batch, index, total, ReviewPromptMode.NORMAL);
    }

    public ReviewPromptPayload build(ReviewExecutionContext context, ReviewExecutionBatch batch,
                                     int index, int total, ReviewPromptMode mode) {
        Map<String, ReviewPromptPayload.PromptFilePayload> grouped =
            new LinkedHashMap<String, ReviewPromptPayload.PromptFilePayload>();
        for (ReviewSemanticUnit unit : batch.getUnits()) {
            ReviewPromptPayload.PromptFilePayload file = grouped.get(unit.getFilePath());
            if (file == null) {
                file = new ReviewPromptPayload.PromptFilePayload();
                file.setFilePath(unit.getFilePath());
                file.setChangeType(unit.getChangeType());
                file.setDiffChunks(new ArrayList<String>());
                file.setContextSnippets(new ArrayList<ReviewCodeSnippet>());
                file.setRiskHints(unit.getRiskHints());
                grouped.put(unit.getFilePath(), file);
            }
            file.getDiffChunks().addAll(diffChunkService.chunk(unit.getDiff()));
            if (StringUtils.hasText(unit.getExpandedCode())) {
                ReviewCodeSnippet snippet = new ReviewCodeSnippet();
                snippet.setTitle(unit.getSemanticType());
                snippet.setStartLine(unit.getStartLine());
                snippet.setEndLine(unit.getEndLine());
                snippet.setContent(unit.getExpandedCode());
                file.getContextSnippets().add(snippet);
            }
            if (Boolean.TRUE.equals(unit.getTruncated())) file.setContextTruncated(Boolean.TRUE);
        }
        List<ReviewPromptPayload.PromptFilePayload> files =
            new ArrayList<ReviewPromptPayload.PromptFilePayload>(grouped.values());
        ReviewPromptPayload prompt = new ReviewPromptPayload();
        prompt.setSystemPrompt(buildSystemPrompt());
        String userPrompt = buildUserPrompt(context, batch.getUnits().size(), files)
            + "\nBatch: " + index + "/" + total + "\n" + outputLimits(mode);
        prompt.setUserPrompt(userPrompt);
        prompt.setFiles(files);
        return prompt;
    }

    private String outputLimits(ReviewPromptMode mode) {
        if (mode == ReviewPromptMode.COMPACT) {
            return "Compact output limits: summary<=200, briefSummary<=80, scoreReason<=160, advice<=200, "
                + "message/suggestion<=160 Chinese characters; suggestedCode must be null.";
        }
        return "Output limits: summary<=500, briefSummary<=80, scoreReason<=300, advice<=500, "
            + "message/suggestion<=300 Chinese characters; suggestedCode must be the shortest necessary replacement; "
            + "return only one comment for the same root cause.";
    }

    private String buildSystemPrompt() {
        return "You are a senior code reviewer. Follow the supplied review rules strictly. "
            + "Return strict JSON with suggestedScore, summary, briefSummary, riskLevel, and comments.";
    }

    private String buildUserPrompt(
        ReviewExecutionContext reviewContext,
        int totalChanges,
        List<ReviewPromptPayload.PromptFilePayload> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("Team review rules").append('\n');
        builder.append(reviewRuleService.getActiveRulesText()).append('\n').append('\n');
        if (StringUtils.hasText(reviewContext.getProjectPromptContent())) {
            builder.append("Project-specific review focus").append('\n');
            builder.append(reviewContext.getProjectPromptContent().trim()).append('\n').append('\n');
        }
        builder.append("Project ID: ").append(reviewContext.getProjectId()).append('\n');
        appendTargetMetadata(builder, reviewContext);
        builder.append("Total changes: ").append(totalChanges).append('\n');
        builder.append("Reviewable files: ").append(files.size()).append('\n');
        builder.append("Return strict JSON only.").append('\n');
        builder.append("Fields:").append('\n');
        builder.append("- suggestedScore: integer base score from 0 to 100 based on overall code quality before programmatic hard-rule deductions").append('\n');
        builder.append("- summary: must be Chinese only, detailed Chinese review summary").append('\n');
        builder.append("- briefSummary: must be Chinese only, concise Chinese notification summary, 1-2 sentences, <= 80 Chinese characters").append('\n');
        builder.append("- riskLevel: must be one of LOW, MEDIUM, HIGH, or CRITICAL").append('\n');
        builder.append("- comments[].filePath").append('\n');
        builder.append("- comments[].line: when the issue can be located from the diff hunk, semantic context, or file content, you must return the exact line number; only return null when the issue truly cannot be mapped to a single line, such as deleted-only code or insufficient evidence").append('\n');
        builder.append("- comments[].severity: must be one of LOW, MEDIUM, HIGH, or CRITICAL").append('\n');
        builder.append("- comments[].category: must be Chinese only, e.g. 功能正确性、安全性、可维护性、性能、项目硬性规范、通用问题").append('\n');
        builder.append("- comments[].message: must be Chinese only").append('\n');
        builder.append("- comments[].suggestion: must be Chinese only").append('\n');
        builder.append("- comments[].suggestedCode: optional short code snippet after applying the recommendation").append('\n');
        builder.append("- comments[].codeStartLine: optional start line of the related code snippet").append('\n');
        builder.append("- comments[].codeEndLine: optional end line of the related code snippet").append('\n');
        builder.append("- comments[].confidence: must be one of HIGH, MEDIUM, or LOW").append('\n');
        builder.append("- comments[].evidenceType: must be one of DIFF_ONLY, DIFF_WITH_CONTEXT, or NEEDS_CONFIRMATION").append('\n');
        builder.append("All explanatory text fields must be in Chinese only, including summary, briefSummary, category, message, suggestion, scoreReason, and advice. ");
        builder.append("Do not output English sentences or English category names in these explanatory text fields. ");
        builder.append("You may use semantic context to evaluate nullability, permissions, transactions, state transitions, and compatibility. ");
        builder.append("Only comment on issues introduced or amplified by this diff. Do not report unrelated historical issues. ");
        builder.append("If an issue is locatable, comments[].line is required and must not be omitted. ");
        builder.append("If comments[].line is null, set evidenceType to NEEDS_CONFIRMATION and explain briefly in the Chinese message why a precise line cannot be determined. ");
        builder.append("If evidence is insufficient, set evidenceType to NEEDS_CONFIRMATION. ");
        builder.append("If there are hard-rule violations, mention them explicitly in summary and briefSummary. ");
        builder.append("suggestedScore must be an integer and should reflect the overall quality before any programmatic hard-rule deductions.");
        return builder.toString();
    }

    private void appendTargetMetadata(StringBuilder builder, ReviewExecutionContext reviewContext) {
        if ("push".equals(reviewContext.getTargetType())) {
            builder.append("Push Branch: ").append(reviewContext.getPushBranch()).append('\n');
            builder.append("Push Range: ").append(reviewContext.getBeforeSha()).append("..")
                .append(reviewContext.getAfterSha()).append('\n');
            builder.append("Commit Count: ").append(reviewContext.getCommitCount()).append('\n');
            return;
        }
        if ("commit".equals(reviewContext.getTargetType())) {
            builder.append("Commit SHA: ").append(reviewContext.getTargetId()).append('\n');
            builder.append("Commit Title: ").append(reviewContext.getTargetTitle()).append('\n');
            return;
        }
        builder.append("Merge Request IID: ").append(reviewContext.getTargetId()).append('\n');
        builder.append("Title: ").append(reviewContext.getTargetTitle()).append('\n');
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

    private Map<String, ReviewFileContext> contextByPath(List<ReviewFileContext> fileContexts) {
        Map<String, ReviewFileContext> result = new HashMap<String, ReviewFileContext>();
        if (fileContexts == null) {
            return result;
        }
        for (ReviewFileContext fileContext : fileContexts) {
            if (fileContext != null && fileContext.getFilePath() != null) {
                result.put(fileContext.getFilePath(), fileContext);
            }
        }
        return result;
    }
}
