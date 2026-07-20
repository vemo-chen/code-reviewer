package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.entity.CodeReviewCommentCodeSnapshotEntity;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentCodeSnapshotMapper;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.model.AggregatedReviewOutput;
import com.vemo.codereview.review.model.MrReviewCompletion;
import com.vemo.codereview.review.model.ReviewEventLifecycle;
import com.vemo.codereview.review.model.ReviewFixStatus;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewCommentDraft;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFileContext;
import com.vemo.codereview.review.model.ReviewSummary;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ReviewResultPersistenceService {

    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;
    private final ReviewCommentCodeSnapshotMapper codeSnapshotMapper;
    private final ReviewEventStoreMapper eventMapper;
    private final ReviewTaskStoreMapper taskMapper;

    public ReviewResultPersistenceService(
        ReviewResultStoreMapper codeReviewResultMapper,
        ReviewCommentStoreMapper codeReviewCommentMapper,
        ReviewCommentCodeSnapshotMapper codeSnapshotMapper,
        ReviewEventStoreMapper eventMapper,
        ReviewTaskStoreMapper taskMapper) {
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
        this.codeSnapshotMapper = codeSnapshotMapper;
        this.eventMapper = eventMapper;
        this.taskMapper = taskMapper;
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

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity persistAggregated(Long taskId, AggregatedReviewOutput output,
                                                     ReviewExecutionContext context) {
        Date now = new Date();
        deleteByTaskId(taskId);
        CodeReviewResultEntity result = new CodeReviewResultEntity();
        ReviewSummary summary = output.getSummary();
        result.setTaskId(taskId);
        result.setProviderName(output.getProviderName());
        result.setModelName(output.getModelName());
        result.setRiskLevel(summary.getRiskLevel());
        result.setSuggestedScore(summary.getSuggestedScore());
        result.setDeductionScore(summary.getDeductionScore());
        result.setFinalScore(summary.getFinalScore());
        result.setSummary(summary.getSummary());
        result.setBriefSummary(summary.getBriefSummary());
        result.setScoreReason(summary.getScoreReason());
        result.setAdvice(summary.getAdvice());
        result.setInputTokens(output.getInputTokens());
        result.setOutputTokens(output.getOutputTokens());
        result.setLatencyMs(output.getLatencyMs());
        result.setRawResponse(output.getRawResponse());
        result.setCreatedAt(now);
        codeReviewResultMapper.insert(result);
        persistComments(result, summary, context, now);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity completeReview(Long taskId, String expectedExecutionToken,
                                                 String providerName, String modelName, ReviewSummary summary,
                                                 ChatCompletionResponse response, ReviewExecutionContext context) {
        CodeReviewTaskEntity task = prepareTaskCompletion(taskId, expectedExecutionToken);
        if (task == null) {
            return null;
        }
        CodeReviewResultEntity result = persist(taskId, providerName, modelName, summary, response, context);
        finishTaskReview(task, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CodeReviewResultEntity completeAggregatedReview(Long taskId, String expectedExecutionToken,
                                                           AggregatedReviewOutput output,
                                                           ReviewExecutionContext context) {
        CodeReviewTaskEntity task = prepareTaskCompletion(taskId, expectedExecutionToken);
        if (task == null) {
            return null;
        }
        CodeReviewResultEntity result = persistAggregated(taskId, output, context);
        finishTaskReview(task, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public MrReviewCompletion completeMrReview(Long taskId, String expectedHead,
                                               AggregatedReviewOutput output, ReviewExecutionContext context) {
        return completeMrReview(taskId, expectedHead, null, output, context);
    }

    @Transactional(rollbackFor = Exception.class)
    public MrReviewCompletion completeMrReview(Long taskId, String expectedHead, String expectedExecutionToken,
                                               AggregatedReviewOutput output, ReviewExecutionContext context) {
        MrReviewCompletion completion = prepareMrReviewCompletion(taskId, expectedHead, expectedExecutionToken);
        if (!completion.isCompleted()) {
            return completion;
        }
        CodeReviewResultEntity result = persistAggregated(taskId, output, context);
        finishMrReview(taskId, result, completion);
        return completion;
    }

    @Transactional(rollbackFor = Exception.class)
    public MrReviewCompletion completeMrReview(Long taskId, String expectedHead,
                                               String providerName, String modelName, ReviewSummary summary,
                                               ChatCompletionResponse response, ReviewExecutionContext context) {
        return completeMrReview(taskId, expectedHead, null, providerName, modelName, summary, response, context);
    }

    @Transactional(rollbackFor = Exception.class)
    public MrReviewCompletion completeMrReview(Long taskId, String expectedHead, String expectedExecutionToken,
                                               String providerName, String modelName, ReviewSummary summary,
                                               ChatCompletionResponse response, ReviewExecutionContext context) {
        MrReviewCompletion completion = prepareMrReviewCompletion(taskId, expectedHead, expectedExecutionToken);
        if (!completion.isCompleted()) {
            return completion;
        }
        CodeReviewResultEntity result = persist(taskId, providerName, modelName, summary, response, context);
        finishMrReview(taskId, result, completion);
        return completion;
    }

    private MrReviewCompletion prepareMrReviewCompletion(Long taskId, String expectedHead, String expectedExecutionToken) {
        CodeReviewTaskEntity initial = taskMapper.selectById(taskId);
        MrReviewCompletion completion = new MrReviewCompletion();
        if (initial == null || initial.getEventId() == null) {
            logResultDiscarded(taskId, "TASK_OR_EVENT_MISSING", null, null, null, false);
            return completion;
        }
        CodeReviewEventEntity event = eventMapper.selectOne(new QueryWrapper<CodeReviewEventEntity>()
            .eq("id", initial.getEventId()).last("FOR UPDATE"));
        CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>()
            .eq("id", taskId).last("FOR UPDATE"));
        if (event == null || task == null) {
            logResultDiscarded(taskId, "TASK_OR_EVENT_MISSING", null, null,
                task == null ? null : task.getStatus(), false);
            return completion;
        }
        boolean tokenMatched = matchesExecutionToken(expectedExecutionToken, task.getExecutionToken());
        if (!ReviewTaskLifecycle.RUNNING.name().equals(task.getStatus())) {
            logResultDiscarded(taskId, "STATUS_CHANGED", expectedHead, event.getMrHeadSha(),
                task.getStatus(), tokenMatched);
            return completion;
        }
        if (!tokenMatched) {
            logResultDiscarded(taskId, "EXECUTION_TOKEN_MISMATCH", expectedHead, event.getMrHeadSha(),
                task.getStatus(), false);
            return completion;
        }
        if (!equals(expectedHead, event.getMrHeadSha())) {
            logResultDiscarded(taskId, "MR_HEAD_CHANGED", expectedHead, event.getMrHeadSha(),
                task.getStatus(), true);
            return completion;
        }
        completion.setCompleted(true);
        return completion;
    }

    private CodeReviewTaskEntity prepareTaskCompletion(Long taskId, String expectedExecutionToken) {
        CodeReviewTaskEntity task = taskMapper.selectOne(new QueryWrapper<CodeReviewTaskEntity>()
            .eq("id", taskId).last("FOR UPDATE"));
        if (task == null) {
            logResultDiscarded(taskId, "TASK_MISSING", null, null, null, false);
            return null;
        }
        boolean tokenMatched = matchesExecutionToken(expectedExecutionToken, task.getExecutionToken());
        if (!ReviewTaskLifecycle.RUNNING.name().equals(task.getStatus())) {
            logResultDiscarded(taskId, "STATUS_CHANGED", null, null, task.getStatus(), tokenMatched);
            return null;
        }
        if (!tokenMatched) {
            logResultDiscarded(taskId, "EXECUTION_TOKEN_MISMATCH", null, null, task.getStatus(), false);
            return null;
        }
        return task;
    }

    private void logResultDiscarded(Long taskId, String reason, String expectedHead, String currentHead,
                                    String taskStatus, boolean executionTokenMatched) {
        log.info("review task result discarded before persistence. taskId={}, reason={}, expectedHead={}, "
                + "currentHead={}, taskStatus={}, executionTokenMatched={}",
            taskId, reason, expectedHead, currentHead, taskStatus, executionTokenMatched);
    }

    private boolean matchesExecutionToken(String expectedExecutionToken, String currentExecutionToken) {
        if (!StringUtils.hasText(expectedExecutionToken)) {
            return true;
        }
        return expectedExecutionToken.equals(currentExecutionToken);
    }

    private void finishMrReview(Long taskId, CodeReviewResultEntity result, MrReviewCompletion completion) {
        CodeReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            completion.setCompleted(false);
            return;
        }
        CodeReviewEventEntity event = eventMapper.selectById(task.getEventId());
        if (event == null) {
            completion.setCompleted(false);
            return;
        }
        task.setStatus(ReviewTaskLifecycle.SUCCESS.name());
        task.setFixStatus(ReviewFixStatus.TO_BE_FIXED.name());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setNextRetryAt(null);
        task.setExecutionToken(null);
        task.setFinishedAt(new Date());
        task.setUpdatedAt(new Date());
        UpdateWrapper<CodeReviewTaskEntity> taskWrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        taskWrapper.eq("id", task.getId())
            .set("status", task.getStatus())
            .set("fix_status", task.getFixStatus())
            .set("error_code", null)
            .set("error_message", null)
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("finished_at", task.getFinishedAt())
            .set("updated_at", task.getUpdatedAt());
        taskMapper.update(null, taskWrapper);
        event.setStatus(ReviewEventLifecycle.PROCESSED.name());
        event.setUpdatedAt(new Date());
        eventMapper.updateById(event);
        completion.setResult(result);
    }

    private void finishTaskReview(CodeReviewTaskEntity task, CodeReviewResultEntity result) {
        task.setStatus(ReviewTaskLifecycle.SUCCESS.name());
        task.setFixStatus(ReviewFixStatus.TO_BE_FIXED.name());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setNextRetryAt(null);
        task.setExecutionToken(null);
        task.setFinishedAt(new Date());
        task.setUpdatedAt(new Date());
        UpdateWrapper<CodeReviewTaskEntity> taskWrapper = new UpdateWrapper<CodeReviewTaskEntity>();
        taskWrapper.eq("id", task.getId())
            .set("status", task.getStatus())
            .set("fix_status", task.getFixStatus())
            .set("error_code", null)
            .set("error_message", null)
            .set("next_retry_at", null)
            .set("execution_token", null)
            .set("finished_at", task.getFinishedAt())
            .set("updated_at", task.getUpdatedAt());
        taskMapper.update(null, taskWrapper);
    }

    private void persistComments(CodeReviewResultEntity resultEntity, ReviewSummary summary,
                                 ReviewExecutionContext context, Date now) {
        if (summary.getComments() == null) return;
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

    private boolean equals(String left, String right) { return left == null ? right == null : left.equals(right); }

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
