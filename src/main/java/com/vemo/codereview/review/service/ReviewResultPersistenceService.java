package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.entity.CodeReviewCommentCodeSnapshotEntity;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentCodeSnapshotMapper;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFileContext;
import com.vemo.codereview.review.model.ReviewSummary;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewResultPersistenceService {

    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;
    private final ReviewCommentCodeSnapshotMapper codeSnapshotMapper;

    public ReviewResultPersistenceService(
        ReviewResultStoreMapper codeReviewResultMapper,
        ReviewCommentStoreMapper codeReviewCommentMapper,
        ReviewCommentCodeSnapshotMapper codeSnapshotMapper) {
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
        this.codeSnapshotMapper = codeSnapshotMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity persist(
        Long taskId,
        String providerName,
        String modelName,
        ReviewSummary summary,
        ChatCompletionResponse response) {
        return persist(taskId, providerName, modelName, summary, response, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity persist(
        Long taskId,
        String providerName,
        String modelName,
        ReviewSummary summary,
        ChatCompletionResponse response,
        ReviewExecutionContext context) {
        Date now = new Date();
        deleteByTaskId(taskId);

        CodeReviewResultEntity resultEntity = new CodeReviewResultEntity();
        resultEntity.setTaskId(taskId);
        resultEntity.setProviderName(providerName);
        resultEntity.setModelName(modelName);
        resultEntity.setRiskLevel(summary.getRiskLevel());
        resultEntity.setSuggestedScore(summary.getSuggestedScore());
        resultEntity.setDeductionScore(summary.getDeductionScore());
        resultEntity.setFinalScore(summary.getFinalScore());
        resultEntity.setSummary(summary.getSummary());
        resultEntity.setBriefSummary(summary.getBriefSummary());
        resultEntity.setScoreReason(summary.getScoreReason());
        resultEntity.setAdvice(summary.getAdvice());
        resultEntity.setInputTokens(response != null && response.getUsage() != null ? response.getUsage().getPromptTokens() : null);
        resultEntity.setOutputTokens(response != null && response.getUsage() != null ? response.getUsage().getCompletionTokens() : null);
        resultEntity.setRawResponse(response != null ? response.getFirstContent() : null);
        resultEntity.setCreatedAt(now);
        codeReviewResultMapper.insert(resultEntity);

        if (summary.getComments() != null) {
            for (ReviewCommentDraft draft : summary.getComments()) {
                CodeReviewCommentEntity commentEntity = new CodeReviewCommentEntity();
                commentEntity.setResultId(resultEntity.getId());
                commentEntity.setFilePath(draft.getFilePath());
                commentEntity.setLineNo(draft.getLine());
                commentEntity.setSeverity(draft.getSeverity());
                commentEntity.setCategory(draft.getCategory());
                commentEntity.setMessage(draft.getMessage());
                commentEntity.setSuggestion(draft.getSuggestion());
                commentEntity.setCommentHash(draft.getCommentHash());
                commentEntity.setIsPosted(Boolean.FALSE);
                commentEntity.setCreatedAt(now);
                codeReviewCommentMapper.insert(commentEntity);
                persistSnapshot(commentEntity, draft, context, now);
            }
        }

        return resultEntity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByTaskId(Long taskId) {
        QueryWrapper<CodeReviewResultEntity> resultWrapper = new QueryWrapper<CodeReviewResultEntity>();
        resultWrapper.eq("task_id", taskId);
        List<CodeReviewResultEntity> existingResults = codeReviewResultMapper.selectList(resultWrapper);
        if (existingResults == null || existingResults.isEmpty()) {
            return;
        }

        for (CodeReviewResultEntity existingResult : existingResults) {
            QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
            commentWrapper.eq("result_id", existingResult.getId());
            List<CodeReviewCommentEntity> comments = codeReviewCommentMapper.selectList(commentWrapper);
            if (comments != null) {
                for (CodeReviewCommentEntity comment : comments) {
                    QueryWrapper<CodeReviewCommentCodeSnapshotEntity> snapshotWrapper =
                        new QueryWrapper<CodeReviewCommentCodeSnapshotEntity>();
                    snapshotWrapper.eq("comment_id", comment.getId());
                    codeSnapshotMapper.delete(snapshotWrapper);
                }
            }
            codeReviewCommentMapper.delete(commentWrapper);
        }
        codeReviewResultMapper.delete(resultWrapper);
    }

    private void persistSnapshot(CodeReviewCommentEntity commentEntity, ReviewCommentDraft draft,
                                 ReviewExecutionContext context, Date now) {
        CodeReviewCommentCodeSnapshotEntity snapshot = new CodeReviewCommentCodeSnapshotEntity();
        snapshot.setCommentId(commentEntity.getId());
        snapshot.setFilePath(draft.getFilePath());
        snapshot.setSuggestedCode(draft.getSuggestedCode());
        snapshot.setStartLine(draft.getCodeStartLine());
        snapshot.setEndLine(draft.getCodeEndLine());
        snapshot.setEvidenceType(draft.getEvidenceType());
        snapshot.setConfidence(draft.getConfidence());
        snapshot.setCreatedAt(now);
        fillCurrentCode(snapshot, draft, context);
        if (snapshot.getCurrentCode() != null || snapshot.getSuggestedCode() != null
            || snapshot.getEvidenceType() != null || snapshot.getConfidence() != null) {
            codeSnapshotMapper.insert(snapshot);
        }
    }

    private void fillCurrentCode(CodeReviewCommentCodeSnapshotEntity snapshot, ReviewCommentDraft draft,
                                 ReviewExecutionContext context) {
        if (context == null || context.getFileContexts() == null) {
            return;
        }
        for (ReviewFileContext fileContext : context.getFileContexts()) {
            if (fileContext == null || !draft.getFilePath().equals(fileContext.getFilePath())) {
                continue;
            }
            snapshot.setRef(fileContext.getRef());
            ReviewCodeSnippet snippet = findSnippet(fileContext, draft);
            if (snippet == null) {
                return;
            }
            snapshot.setCurrentCode(snippet.getContent());
            if (snapshot.getStartLine() == null) {
                snapshot.setStartLine(snippet.getStartLine());
            }
            if (snapshot.getEndLine() == null) {
                snapshot.setEndLine(snippet.getEndLine());
            }
            return;
        }
    }

    private ReviewCodeSnippet findSnippet(ReviewFileContext fileContext, ReviewCommentDraft draft) {
        if (fileContext.getSnippets() == null || fileContext.getSnippets().isEmpty()) {
            return null;
        }
        if (draft.getLine() == null) {
            return fileContext.getSnippets().get(0);
        }
        for (ReviewCodeSnippet snippet : fileContext.getSnippets()) {
            if (snippet.getStartLine() != null && snippet.getEndLine() != null
                && draft.getLine() >= snippet.getStartLine() && draft.getLine() <= snippet.getEndLine()) {
                return snippet;
            }
        }
        return fileContext.getSnippets().get(0);
    }
}
