package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import java.util.ArrayList;
import java.util.List;
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
        for (GitLabChangesPayload.Change change : reviewableChanges) {
            ReviewPromptPayload.PromptFilePayload promptFile = new ReviewPromptPayload.PromptFilePayload();
            promptFile.setFilePath(change.getNewPath() != null ? change.getNewPath() : change.getOldPath());
            promptFile.setChangeType(resolveChangeType(change));
            promptFile.setDiffChunks(diffChunkService.chunk(change.getDiff()));
            files.add(promptFile);
        }

        ReviewPromptPayload prompt = new ReviewPromptPayload();
        prompt.setSystemPrompt(buildSystemPrompt());
        prompt.setUserPrompt(buildUserPrompt(reviewContext, allChanges.size(), files));
        prompt.setFiles(files);
        return prompt;
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
        builder.append("- summary: detailed Chinese review summary").append('\n');
        builder.append("- briefSummary: concise Chinese notification summary, 1-2 sentences, <= 80 Chinese characters").append('\n');
        builder.append("- riskLevel: LOW, MEDIUM, HIGH, or CRITICAL").append('\n');
        builder.append("- comments[].filePath").append('\n');
        builder.append("- comments[].line").append('\n');
        builder.append("- comments[].severity").append('\n');
        builder.append("- comments[].category: use Chinese category names only, e.g. 鍔熻兘姝ｇ‘鎬с€佸畨鍏ㄦ€с€佸彲缁存姢鎬с€佹€ц兘銆侀」鐩‖鎬ц鑼冦€侀€氱敤闂").append('\n');
        builder.append("- comments[].message").append('\n');
        builder.append("- comments[].suggestion").append('\n');
        builder.append("If there are hard-rule violations, mention them explicitly in summary and briefSummary. ");
        builder.append("suggestedScore must be an integer and should reflect the overall quality before any programmatic hard-rule deductions.");
        return builder.toString();
    }

    private void appendTargetMetadata(StringBuilder builder, ReviewExecutionContext reviewContext) {
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
}