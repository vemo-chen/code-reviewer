package com.vemo.codereview.review.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.platform.gitlab.model.GitLabBranchPayload;
import com.vemo.codereview.platform.gitlab.model.GitLabCommitPayload;
import com.vemo.codereview.platform.gitlab.service.GitLabReviewTargetService;
import com.vemo.codereview.project.model.ProjectCustomReviewBatchRequest;
import com.vemo.codereview.project.model.ProjectCustomReviewBatchResponse;
import com.vemo.codereview.project.service.GitLabProjectResolver;
import com.vemo.codereview.review.entity.CodeReviewBatchEntity;
import com.vemo.codereview.review.entity.CodeReviewBatchTaskRelEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewBatchStoreMapper;
import com.vemo.codereview.review.mapper.ReviewBatchTaskRelStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.review.model.ReviewTaskLifecycle;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectCustomReviewBatchService {

    private static final String TASK_TYPE_PUSH_REVIEW = "PUSH_REVIEW";
    private static final String MODE_SKIP_REVIEWED = "SKIP_REVIEWED";
    private static final String MODE_FORCE_REREVIEW = "FORCE_REREVIEW";

    private final GitLabReviewTargetService gitLabReviewTargetService;
    private final GitLabProjectResolver gitLabProjectResolver;
    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewEventStoreMapper codeReviewEventMapper;
    private final ReviewBatchStoreMapper codeReviewBatchMapper;
    private final ReviewBatchTaskRelStoreMapper codeReviewBatchTaskRelMapper;
    private final ReviewTaskManualRetryService reviewTaskManualRetryService;
    private final ReviewStateService reviewStateService;
    private final ManualReviewEventFactory manualReviewEventFactory;
    private final CurrentUserService currentUserService;

    public ProjectCustomReviewBatchService(
        GitLabReviewTargetService gitLabReviewTargetService,
        GitLabProjectResolver gitLabProjectResolver,
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewEventStoreMapper codeReviewEventMapper,
        ReviewBatchStoreMapper codeReviewBatchMapper,
        ReviewBatchTaskRelStoreMapper codeReviewBatchTaskRelMapper,
        ReviewTaskManualRetryService reviewTaskManualRetryService,
        ReviewStateService reviewStateService,
        ManualReviewEventFactory manualReviewEventFactory,
        CurrentUserService currentUserService) {
        this.gitLabReviewTargetService = gitLabReviewTargetService;
        this.gitLabProjectResolver = gitLabProjectResolver;
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewBatchMapper = codeReviewBatchMapper;
        this.codeReviewBatchTaskRelMapper = codeReviewBatchTaskRelMapper;
        this.reviewTaskManualRetryService = reviewTaskManualRetryService;
        this.reviewStateService = reviewStateService;
        this.manualReviewEventFactory = manualReviewEventFactory;
        this.currentUserService = currentUserService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectCustomReviewBatchResponse createBatch(
        ProjectProfileEntity project,
        ProjectCustomReviewBatchRequest request) {
        if (project == null || project.getId() == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }
        validateProject(project);

        ParsedRequest parsedRequest = parseRequest(request);
        List<String> branches = resolveBranches(project, parsedRequest);
        LinkedHashMap<String, CommitCandidate> commitCandidates = loadCommitCandidates(project, branches, parsedRequest);

        Date now = new Date();
        Long currentUserId = currentUserService.requireCurrentUserId();
        String currentUserName = currentUserService.requireCurrentUserDisplayName();

        CodeReviewBatchEntity batch = new CodeReviewBatchEntity();
        batch.setProjectId(project.getId());
        batch.setTriggerType("CUSTOM_REVIEW");
        batch.setReviewMode(parsedRequest.reviewMode);
        batch.setStartTime(parsedRequest.startTime);
        batch.setEndTime(parsedRequest.endTime);
        batch.setBranchScope(joinBranches(branches));
        batch.setStatus("RUNNING");
        batch.setCreatedBy(currentUserId);
        batch.setCreatedByName(currentUserName);
        batch.setTotalCommitCount(commitCandidates.size());
        batch.setCreatedTaskCount(0);
        batch.setRetriedTaskCount(0);
        batch.setSkippedReviewedCount(0);
        batch.setSkippedRunningCount(0);
        batch.setSkippedFailedCount(0);
        batch.setFailedCount(0);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        codeReviewBatchMapper.insert(batch);

        int createdTaskCount = 0;
        int retriedTaskCount = 0;
        int skippedReviewedCount = 0;
        int skippedRunningCount = 0;
        int skippedFailedCount = 0;
        int failedCount = 0;

        for (CommitCandidate candidate : commitCandidates.values()) {
            try {
                CodeReviewTaskEntity existingTask = findExistingCommitTask(project.getId(), candidate.commitSha);
                if (existingTask == null) {
                    CodeReviewTaskEntity createdTask = createTaskForCommit(project, candidate, currentUserId, currentUserName, now);
                    insertBatchTaskRel(batch.getId(), createdTask.getId(), candidate, "CREATED", "Task created from custom review batch", now);
                    createdTaskCount++;
                    continue;
                }

                String status = normalize(existingTask.getStatus());
                if (ReviewTaskLifecycle.PENDING.name().equals(status) || ReviewTaskLifecycle.RUNNING.name().equals(status)) {
                    insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "SKIPPED_RUNNING",
                        "Task is already pending or running", now);
                    skippedRunningCount++;
                    continue;
                }

                if (ReviewTaskLifecycle.SUCCESS.name().equals(status)) {
                    if (MODE_FORCE_REREVIEW.equals(parsedRequest.reviewMode)) {
                        reviewTaskManualRetryService.resetAndDispatch(existingTask);
                        insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "RETRIED",
                            "Existing reviewed task was resubmitted", now);
                        retriedTaskCount++;
                    } else {
                        insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "SKIPPED_REVIEWED",
                            "Existing reviewed task was skipped", now);
                        skippedReviewedCount++;
                    }
                    continue;
                }

                if (ReviewTaskLifecycle.FAILED.name().equals(status)) {
                    if (MODE_FORCE_REREVIEW.equals(parsedRequest.reviewMode)) {
                        reviewTaskManualRetryService.resetAndDispatch(existingTask);
                        insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "RETRIED",
                            "Existing failed task was resubmitted", now);
                        retriedTaskCount++;
                    } else {
                        insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "SKIPPED_FAILED",
                            "Existing failed task was skipped", now);
                        skippedFailedCount++;
                    }
                    continue;
                }

                insertBatchTaskRel(batch.getId(), existingTask.getId(), candidate, "SKIPPED_RUNNING",
                    "Task status is not eligible for custom review", now);
                skippedRunningCount++;
            } catch (RuntimeException ex) {
                failedCount++;
            }
        }

        batch.setCreatedTaskCount(createdTaskCount);
        batch.setRetriedTaskCount(retriedTaskCount);
        batch.setSkippedReviewedCount(skippedReviewedCount);
        batch.setSkippedRunningCount(skippedRunningCount);
        batch.setSkippedFailedCount(skippedFailedCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(failedCount > 0 && failedCount == commitCandidates.size() ? "FAILED" : "FINISHED");
        batch.setUpdatedAt(new Date());
        batch.setFinishedAt(new Date());
        codeReviewBatchMapper.updateById(batch);

        ProjectCustomReviewBatchResponse response = new ProjectCustomReviewBatchResponse();
        response.setBatchId(batch.getId());
        response.setTotalCommitCount(commitCandidates.size());
        response.setCreatedTaskCount(createdTaskCount);
        response.setRetriedTaskCount(retriedTaskCount);
        response.setSkippedReviewedCount(skippedReviewedCount);
        response.setSkippedRunningCount(skippedRunningCount);
        response.setSkippedFailedCount(skippedFailedCount);
        response.setFailedCount(failedCount);
        return response;
    }

    private void validateProject(ProjectProfileEntity project) {
        if (project.getGitlabProjectId() == null) {
            throw new DomainException("PROJECT_GITLAB_ID_REQUIRED", "GitLab project id is required");
        }
        if (!StringUtils.hasText(project.getGitlabProjectUrl())) {
            throw new DomainException("PROJECT_URL_REQUIRED", "GitLab project URL is required");
        }
        if (!StringUtils.hasText(project.getGitlabWebhookToken())) {
            throw new DomainException("GITLAB_TOKEN_REQUIRED", "GitLab token is required");
        }
    }

    private ParsedRequest parseRequest(ProjectCustomReviewBatchRequest request) {
        if (request == null) {
            throw new DomainException("CUSTOM_REVIEW_REQUEST_REQUIRED", "Custom review request is required");
        }
        if (!StringUtils.hasText(request.getStartTime()) || !StringUtils.hasText(request.getEndTime())) {
            throw new DomainException("CUSTOM_REVIEW_TIME_RANGE_REQUIRED", "Custom review time range is required");
        }
        Date startTime = parseUserDateTime(request.getStartTime().trim(), "CUSTOM_REVIEW_TIME_RANGE_INVALID");
        Date endTime = parseUserDateTime(request.getEndTime().trim(), "CUSTOM_REVIEW_TIME_RANGE_INVALID");
        if (endTime.before(startTime)) {
            throw new DomainException("CUSTOM_REVIEW_TIME_RANGE_INVALID", "Custom review time range is invalid");
        }

        String reviewMode = StringUtils.hasText(request.getReviewMode())
            ? request.getReviewMode().trim() : MODE_SKIP_REVIEWED;
        if (!MODE_SKIP_REVIEWED.equals(reviewMode) && !MODE_FORCE_REREVIEW.equals(reviewMode)) {
            throw new DomainException("CUSTOM_REVIEW_MODE_INVALID", "Custom review mode is invalid");
        }

        ParsedRequest parsed = new ParsedRequest();
        parsed.startTime = startTime;
        parsed.endTime = endTime;
        parsed.reviewMode = reviewMode;
        parsed.gitLabSince = formatGitLabDateTime(startTime);
        parsed.gitLabUntil = formatGitLabDateTime(endTime);
        parsed.requestedBranches = normalizeRequestedBranches(request.getReviewBranches());
        return parsed;
    }

    private List<String> resolveBranches(ProjectProfileEntity project, ParsedRequest parsedRequest) {
        LinkedHashSet<String> configuredBranches = parseBranches(project.getReviewBranches());
        if (!configuredBranches.isEmpty()) {
            if (parsedRequest.requestedBranches.isEmpty()) {
                return new ArrayList<String>(configuredBranches);
            }
            List<String> effectiveBranches = new ArrayList<String>();
            for (String branch : parsedRequest.requestedBranches) {
                if (!configuredBranches.contains(branch)) {
                    throw new DomainException("CUSTOM_REVIEW_BRANCH_SCOPE_INVALID", "Custom review branch scope is invalid");
                }
                effectiveBranches.add(branch);
            }
            return effectiveBranches;
        }
        if (!parsedRequest.requestedBranches.isEmpty()) {
            return new ArrayList<String>(parsedRequest.requestedBranches);
        }
        List<GitLabBranchPayload> branches = gitLabProjectResolver.listBranches(
            project.getGitlabProjectUrl(),
            project.getGitlabWebhookToken()
        );
        if (branches == null || branches.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (GitLabBranchPayload branch : branches) {
            if (branch != null && StringUtils.hasText(branch.getName())) {
                names.add(branch.getName().trim());
            }
        }
        return new ArrayList<String>(names);
    }

    private LinkedHashMap<String, CommitCandidate> loadCommitCandidates(
        ProjectProfileEntity project,
        List<String> branches,
        ParsedRequest parsedRequest) {
        LinkedHashMap<String, CommitCandidate> result = new LinkedHashMap<String, CommitCandidate>();
        for (String branch : branches) {
            if (!StringUtils.hasText(branch)) {
                continue;
            }
            List<GitLabCommitPayload> commits = gitLabReviewTargetService.listBranchCommits(
                project.getGitlabProjectUrl(),
                project.getGitlabProjectId(),
                branch,
                parsedRequest.gitLabSince,
                parsedRequest.gitLabUntil,
                project.getGitlabWebhookToken()
            );
            if (commits == null) {
                continue;
            }
            for (GitLabCommitPayload commit : commits) {
                if (commit == null || !StringUtils.hasText(commit.getId())) {
                    continue;
                }
                String commitSha = commit.getId().trim();
                if (!result.containsKey(commitSha)) {
                    CommitCandidate candidate = new CommitCandidate();
                    candidate.commitSha = commitSha;
                    candidate.branch = branch.trim();
                    candidate.commit = commit;
                    result.put(commitSha, candidate);
                }
            }
        }
        return result;
    }

    private CodeReviewTaskEntity findExistingCommitTask(Long projectId, String commitSha) {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        wrapper.eq("project_id", projectId)
            .eq("task_type", TASK_TYPE_PUSH_REVIEW)
            .eq("target_id", commitSha)
            .orderByDesc("id");
        List<CodeReviewTaskEntity> tasks = codeReviewTaskMapper.selectList(wrapper);
        if (tasks == null) return null;
        for (CodeReviewTaskEntity task : tasks) {
            CodeReviewEventEntity event = task.getEventId() == null ? null : codeReviewEventMapper.selectById(task.getEventId());
            if (event != null && "commit".equalsIgnoreCase(event.getObjectType())) return task;
        }
        return null;
    }

    private CodeReviewTaskEntity createTaskForCommit(
        ProjectProfileEntity project,
        CommitCandidate candidate,
        Long currentUserId,
        String currentUserName,
        Date now) {
        CodeReviewEventEntity event = manualReviewEventFactory.buildCommitEvent(
            project,
            candidate.commit,
            candidate.branch,
            currentUserId,
            currentUserName,
            now
        );
        codeReviewEventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType(TASK_TYPE_PUSH_REVIEW);
        task.setSourcePlatform(StringUtils.hasText(project.getSourcePlatform()) ? project.getSourcePlatform().trim() : "gitlab");
        task.setProjectId(project.getId());
        task.setTargetId(candidate.commitSha);
        task.setTargetTitle(resolveTaskTitle(candidate.commit));
        task.setStatus(ReviewTaskLifecycle.PENDING.name());
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);
        reviewStateService.markEventTaskCreated(event);
        reviewTaskManualRetryService.dispatch(task.getId());
        return task;
    }

    private void insertBatchTaskRel(
        Long batchId,
        Long taskId,
        CommitCandidate candidate,
        String actionType,
        String message,
        Date now) {
        CodeReviewBatchTaskRelEntity rel = new CodeReviewBatchTaskRelEntity();
        rel.setBatchId(batchId);
        rel.setTaskId(taskId);
        rel.setTargetId(candidate.commitSha);
        rel.setSubmitBranch(candidate.branch);
        rel.setActionType(actionType);
        rel.setMessage(message);
        rel.setCreatedAt(now);
        rel.setUpdatedAt(now);
        codeReviewBatchTaskRelMapper.insert(rel);
    }

    private LinkedHashSet<String> parseBranches(String reviewBranches) {
        LinkedHashSet<String> branches = new LinkedHashSet<String>();
        if (!StringUtils.hasText(reviewBranches)) {
            return branches;
        }
        String[] tokens = reviewBranches.split("[,;\\s]+");
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                branches.add(token.trim());
            }
        }
        return branches;
    }

    private LinkedHashSet<String> normalizeRequestedBranches(List<String> reviewBranches) {
        LinkedHashSet<String> branches = new LinkedHashSet<String>();
        if (reviewBranches == null || reviewBranches.isEmpty()) {
            return branches;
        }
        for (String branch : reviewBranches) {
            if (StringUtils.hasText(branch)) {
                branches.add(branch.trim());
            }
        }
        return branches;
    }

    private String joinBranches(List<String> branches) {
        if (branches == null || branches.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String branch : branches) {
            if (!StringUtils.hasText(branch)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(branch.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private Date parseUserDateTime(String raw, String errorCode) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            return format.parse(raw);
        } catch (ParseException ex) {
            throw new DomainException(errorCode, "Custom review time range is invalid");
        }
    }

    private String formatGitLabDateTime(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(date);
    }

    private String resolveTaskTitle(GitLabCommitPayload commit) {
        if (commit == null) {
            return null;
        }
        if (StringUtils.hasText(commit.getTitle())) {
            return commit.getTitle().trim();
        }
        if (StringUtils.hasText(commit.getMessage())) {
            String message = commit.getMessage().trim();
            int newLine = message.indexOf('\n');
            return newLine > 0 ? message.substring(0, newLine).trim() : message;
        }
        return StringUtils.hasText(commit.getId()) ? commit.getId().trim() : null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private static class ParsedRequest {
        private Date startTime;
        private Date endTime;
        private String reviewMode;
        private String gitLabSince;
        private String gitLabUntil;
        private LinkedHashSet<String> requestedBranches = new LinkedHashSet<String>();
    }

    private static class CommitCandidate {
        private String commitSha;
        private String branch;
        private GitLabCommitPayload commit;
    }
}
