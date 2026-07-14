package com.vemo.codereview.platform.gitlab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitLabCommentPublisher {

    private final GitLabReviewTargetService gitLabReviewTargetService;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;

    public GitLabCommentPublisher(
        GitLabReviewTargetService gitLabReviewTargetService,
        ReviewCommentStoreMapper codeReviewCommentMapper) {
        this.gitLabReviewTargetService = gitLabReviewTargetService;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishMergeRequest(Long projectId, String mergeRequestIid, CodeReviewResultEntity result) {
        return publishMergeRequest(projectId, mergeRequestIid, result, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishMergeRequest(Long projectId, String mergeRequestIid, CodeReviewResultEntity result, String token) {
        return publishMergeRequest(null, projectId, mergeRequestIid, result, token);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishMergeRequest(String gitlabProjectUrl, Long projectId, String mergeRequestIid,
                                       CodeReviewResultEntity result, String token) {
        List<CodeReviewCommentEntity> comments = loadComments(result);
        if (!hasUnpostedComments(comments)) {
            return false;
        }
        gitLabReviewTargetService.publishMergeRequestNote(
            gitlabProjectUrl,
            projectId,
            mergeRequestIid,
            buildNoteBody(result, comments),
            token
        );
        markCommentsAsPosted(comments);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishCommit(Long projectId, String commitSha, CodeReviewResultEntity result) {
        return publishCommit(projectId, commitSha, result, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishCommit(Long projectId, String commitSha, CodeReviewResultEntity result, String token) {
        return publishCommit(null, projectId, commitSha, result, token);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishCommit(String gitlabProjectUrl, Long projectId, String commitSha,
                                 CodeReviewResultEntity result, String token) {
        List<CodeReviewCommentEntity> comments = loadComments(result);
        if (!hasUnpostedComments(comments)) {
            return false;
        }
        gitLabReviewTargetService.publishCommitNote(
            gitlabProjectUrl,
            projectId,
            commitSha,
            buildNoteBody(result, comments),
            token
        );
        markCommentsAsPosted(comments);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishPushRange(String gitlabProjectUrl, Long projectId, ReviewExecutionContext context,
                                    CodeReviewResultEntity result, String token) {
        List<CodeReviewCommentEntity> comments = loadComments(result);
        if (!hasUnpostedComments(comments)) return false;
        gitLabReviewTargetService.publishCommitNote(gitlabProjectUrl, projectId, context.getAfterSha(),
            buildPushRangeNoteBody(context, result, comments), token);
        markCommentsAsPosted(comments);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishMergedMrCommit(String gitlabProjectUrl, Long projectId, String mergeCommitSha,
                                         String mrTitle, String targetBranch, CodeReviewResultEntity result,
                                         String token) {
        List<CodeReviewCommentEntity> comments = loadComments(result);
        gitLabReviewTargetService.publishCommitNote(
            gitlabProjectUrl,
            projectId,
            mergeCommitSha,
            buildMergedMrCommitNoteBody(mergeCommitSha, mrTitle, targetBranch, result, comments),
            token
        );
        return true;
    }

    private String buildPushRangeNoteBody(ReviewExecutionContext context, CodeReviewResultEntity result,
                                          List<CodeReviewCommentEntity> comments) {
        StringBuilder body = new StringBuilder();
        body.append("## AI Code Review - Push Range\n");
        body.append("- Branch: ").append(context.getPushBranch()).append('\n');
        body.append("- Range: ").append(context.getBeforeSha()).append("..").append(context.getAfterSha()).append('\n');
        body.append("- Commit Count: ").append(context.getCommitCount()).append('\n');
        body.append(buildNoteBody(result, comments));
        return body.toString();
    }

    private String buildMergedMrCommitNoteBody(String mergeCommitSha, String mrTitle, String targetBranch,
                                               CodeReviewResultEntity result,
                                               List<CodeReviewCommentEntity> comments) {
        StringBuilder body = new StringBuilder();
        body.append("## AI Code Review - Merged MR\n");
        body.append("- MR: ").append(mrTitle).append('\n');
        body.append("- Target Branch: ").append(targetBranch).append('\n');
        body.append("- Merge Commit: ").append(mergeCommitSha).append('\n');
        appendReviewDetails(body, result, comments);
        if (comments == null || comments.isEmpty()) {
            body.append("No issues requiring fixes were found.\n");
        }
        return body.toString();
    }

    private List<CodeReviewCommentEntity> loadComments(CodeReviewResultEntity result) {
        QueryWrapper<CodeReviewCommentEntity> wrapper = new QueryWrapper<CodeReviewCommentEntity>();
        wrapper.eq("result_id", result.getId());
        return codeReviewCommentMapper.selectList(wrapper);
    }

    private boolean hasUnpostedComments(List<CodeReviewCommentEntity> comments) {
        if (comments == null || comments.isEmpty()) {
            return false;
        }
        for (CodeReviewCommentEntity comment : comments) {
            if (!Boolean.TRUE.equals(comment.getIsPosted())) {
                return true;
            }
        }
        return false;
    }

    private void markCommentsAsPosted(List<CodeReviewCommentEntity> comments) {
        Date now = new Date();
        for (CodeReviewCommentEntity comment : comments) {
            comment.setIsPosted(Boolean.TRUE);
            comment.setPostedAt(now);
            codeReviewCommentMapper.updateById(comment);
        }
    }

    private String buildNoteBody(CodeReviewResultEntity result, List<CodeReviewCommentEntity> comments) {
        StringBuilder builder = new StringBuilder();
        builder.append("## AI Code Review").append('\n');
        appendReviewDetails(builder, result, comments);
        return builder.toString();
    }

    private void appendReviewDetails(StringBuilder builder, CodeReviewResultEntity result,
                                     List<CodeReviewCommentEntity> comments) {
        builder.append("- Risk Level: ").append(result.getRiskLevel()).append('\n');
        builder.append("- Summary: ").append(result.getSummary()).append('\n');
        if (result.getAdvice() != null && !result.getAdvice().trim().isEmpty()) {
            builder.append("- Advice: ").append(result.getAdvice()).append('\n');
        }
        builder.append('\n');

        for (CodeReviewCommentEntity comment : comments) {
            builder.append("### ")
                .append(comment.getSeverity())
                .append(" | ")
                .append(comment.getCategory())
                .append('\n');
            builder.append("- File: ").append(comment.getFilePath()).append('\n');
            if (comment.getLineNo() != null) {
                builder.append("- Line: ").append(comment.getLineNo()).append('\n');
            }
            builder.append("- Issue: ").append(comment.getMessage()).append('\n');
            if (comment.getSuggestion() != null && !comment.getSuggestion().trim().isEmpty()) {
                builder.append("- Suggestion: ").append(comment.getSuggestion()).append('\n');
            }
            builder.append('\n');
        }
    }
}
