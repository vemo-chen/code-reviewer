package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewSummary;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewResultPersistenceService {

    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;

    public ReviewResultPersistenceService(
        ReviewResultStoreMapper codeReviewResultMapper,
        ReviewCommentStoreMapper codeReviewCommentMapper) {
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity persist(
        Long taskId,
        String providerName,
        String modelName,
        ReviewSummary summary,
        ChatCompletionResponse response) {
        Date now = new Date();
        deleteExistingResult(taskId);

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
            }
        }

        return resultEntity;
    }

    private void deleteExistingResult(Long taskId) {
        QueryWrapper<CodeReviewResultEntity> resultWrapper = new QueryWrapper<CodeReviewResultEntity>();
        resultWrapper.eq("task_id", taskId);
        List<CodeReviewResultEntity> existingResults = codeReviewResultMapper.selectList(resultWrapper);
        if (existingResults == null || existingResults.isEmpty()) {
            return;
        }

        for (CodeReviewResultEntity existingResult : existingResults) {
            QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
            commentWrapper.eq("result_id", existingResult.getId());
            codeReviewCommentMapper.delete(commentWrapper);
        }
        codeReviewResultMapper.delete(resultWrapper);
    }
}