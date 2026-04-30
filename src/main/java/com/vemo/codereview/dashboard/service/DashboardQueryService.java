package com.vemo.codereview.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.dashboard.model.DeveloperStatsResponse;
import com.vemo.codereview.dashboard.model.ProjectOverviewResponse;
import com.vemo.codereview.dashboard.model.ReviewSubmitterOptionResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskDetailResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskPageResponse;
import com.vemo.codereview.dashboard.model.ReviewTaskQueryRequest;
import com.vemo.codereview.dashboard.model.ScoreStatsResponse;
import com.vemo.codereview.project.service.ProjectPermissionService;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.entity.UserProjectRelEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import com.vemo.codereview.user.mapper.UserProjectRelMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardQueryService {

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewEventStoreMapper codeReviewEventMapper;
    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final ReviewCommentStoreMapper codeReviewCommentMapper;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectProfileMapper projectProfileMapper;
    private final UserProjectRelMapper userProjectRelMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public DashboardQueryService(
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewEventStoreMapper codeReviewEventMapper,
        ReviewResultStoreMapper codeReviewResultMapper,
        ReviewCommentStoreMapper codeReviewCommentMapper,
        ProjectPermissionService projectPermissionService,
        ProjectProfileMapper projectProfileMapper,
        UserProjectRelMapper userProjectRelMapper,
        UserMapper userMapper,
        ObjectMapper objectMapper) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.codeReviewCommentMapper = codeReviewCommentMapper;
        this.projectPermissionService = projectPermissionService;
        this.projectProfileMapper = projectProfileMapper;
        this.userProjectRelMapper = userProjectRelMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public ReviewTaskPageResponse pageReviewTasks(long pageNo, long pageSize) {
        ReviewTaskQueryRequest request = new ReviewTaskQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return pageReviewTasks(request);
    }

    public ReviewTaskPageResponse pageReviewTasks(ReviewTaskQueryRequest request) {
        long pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        long pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        if (!projectPermissionService.isAdmin() && accessibleProjectIds.isEmpty()) {
            return emptyReviewTaskPage(pageNo, pageSize);
        }

        Set<Long> taskIds = resolveTaskIdsForCrossTableFilters(request);
        if (taskIds != null && taskIds.isEmpty()) {
            return emptyReviewTaskPage(pageNo, pageSize);
        }

        QueryWrapper<CodeReviewTaskEntity> pageWrapper = buildReviewTaskWrapper(request, taskIds, accessibleProjectIds);
        pageWrapper.orderByDesc("id");

        Page<CodeReviewTaskEntity> page = codeReviewTaskMapper.selectPage(new Page<CodeReviewTaskEntity>(pageNo, pageSize), pageWrapper);
        List<CodeReviewTaskEntity> tasks = page.getRecords();
        Map<Long, CodeReviewEventEntity> eventMap = buildEventMap(tasks);
        Map<Long, CodeReviewResultEntity> resultMap = buildResultMap(tasks);

        List<ReviewTaskPageResponse.Item> records = new ArrayList<ReviewTaskPageResponse.Item>();
        for (CodeReviewTaskEntity task : tasks) {
            records.add(buildReviewTaskItem(task, eventMap.get(task.getEventId()), resultMap.get(task.getId())));
        }

        ReviewTaskPageResponse response = new ReviewTaskPageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotal());
        response.setRecords(records);
        return response;
    }

    private QueryWrapper<CodeReviewTaskEntity> buildReviewTaskWrapper(
        ReviewTaskQueryRequest request,
        Set<Long> taskIds,
        List<Long> accessibleProjectIds) {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        if (!projectPermissionService.isAdmin()) {
            if (accessibleProjectIds == null || accessibleProjectIds.isEmpty()) {
                wrapper.apply("1 = 0");
                return wrapper;
            }
            wrapper.in("project_id", accessibleProjectIds);
        }
        if (request.getProjectId() != null) {
            wrapper.eq("project_id", request.getProjectId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq("status", request.getStatus());
        }
        if (StringUtils.hasText(request.getFixStatus())) {
            wrapper.eq("fix_status", request.getFixStatus().trim());
        }
        if (StringUtils.hasText(request.getTargetTitle())) {
            wrapper.like("target_title", request.getTargetTitle().trim());
        }
        if (StringUtils.hasText(request.getStartDate())) {
            wrapper.ge("created_at", parseDate(request.getStartDate()));
        }
        if (StringUtils.hasText(request.getEndDate())) {
            wrapper.lt("created_at", parseEndExclusive(request.getEndDate()));
        }
        if (taskIds != null) {
            wrapper.in("id", taskIds);
        }
        return wrapper;
    }

    public ReviewTaskDetailResponse getReviewTaskDetail(Long taskId) {
        CodeReviewTaskEntity task = codeReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new DomainException("TASK_NOT_FOUND", "Review task not found");
        }
        projectPermissionService.requireProjectAccess(task.getProjectId());

        CodeReviewEventEntity event = task.getEventId() == null ? null : codeReviewEventMapper.selectById(task.getEventId());
        CodeReviewResultEntity result = findResultByTaskId(taskId);
        ProjectProfileEntity project = task.getProjectId() == null ? null : projectProfileMapper.selectById(task.getProjectId());
        UserEntity owner = resolveUser(project == null ? null : project.getOwnerUserId());
        UserEntity submittedByUser = resolveUser(task.getFixSubmittedBy());
        UserEntity reviewedByUser = resolveUser(task.getFixReviewedBy());
        List<CodeReviewCommentEntity> comments = Collections.emptyList();
        if (result != null && result.getId() != null) {
            QueryWrapper<CodeReviewCommentEntity> commentWrapper = new QueryWrapper<CodeReviewCommentEntity>();
            commentWrapper.eq("result_id", result.getId()).orderByAsc("id");
            comments = codeReviewCommentMapper.selectList(commentWrapper);
        }

        ReviewTaskDetailResponse response = new ReviewTaskDetailResponse();
        response.setTaskId(task.getId());
        response.setProjectId(task.getProjectId());
        response.setProjectName(event != null ? event.getProjectName() : null);
        response.setTargetId(task.getTargetId());
        response.setTargetTitle(task.getTargetTitle());
        response.setSubmitBranch(resolveSubmitBranch(event));
        response.setStatus(task.getStatus());
        response.setOwnerUserId(project == null ? null : project.getOwnerUserId());
        response.setOwnerDisplayName(resolveUserDisplayName(owner));
        response.setRetryCount(task.getRetryCount());
        response.setOperatorName(event != null ? event.getOperatorName() : null);
        response.setCreatedAt(task.getCreatedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setFixStatus(task.getFixStatus());
        response.setFixSubmittedBy(task.getFixSubmittedBy());
        response.setFixSubmittedByName(resolveUserDisplayName(submittedByUser));
        response.setFixSubmittedAt(task.getFixSubmittedAt());
        response.setFixReviewedBy(task.getFixReviewedBy());
        response.setFixReviewedByName(resolveUserDisplayName(reviewedByUser));
        response.setFixReviewedAt(task.getFixReviewedAt());
        response.setFixReviewComment(task.getFixReviewComment());
        if (result != null) {
            response.setRiskLevel(result.getRiskLevel());
            response.setSuggestedScore(result.getSuggestedScore());
            response.setDeductionScore(result.getDeductionScore());
            response.setFinalScore(result.getFinalScore());
            response.setScoreReason(result.getScoreReason());
            response.setSummary(result.getSummary());
            response.setBriefSummary(result.getBriefSummary());
        }

        List<ReviewTaskDetailResponse.CommentItem> commentItems = new ArrayList<ReviewTaskDetailResponse.CommentItem>();
        for (CodeReviewCommentEntity comment : comments) {
            ReviewTaskDetailResponse.CommentItem item = new ReviewTaskDetailResponse.CommentItem();
            item.setId(comment.getId());
            item.setFilePath(comment.getFilePath());
            item.setLineNo(comment.getLineNo());
            item.setSeverity(comment.getSeverity());
            item.setCategory(comment.getCategory());
            item.setMessage(comment.getMessage());
            item.setSuggestion(comment.getSuggestion());
            item.setIsPosted(comment.getIsPosted());
            item.setCreatedAt(comment.getCreatedAt());
            commentItems.add(item);
        }
        response.setComments(commentItems);
        return response;
    }


    public ReviewSubmitterOptionResponse getReviewSubmitters() {
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        ReviewSubmitterOptionResponse response = new ReviewSubmitterOptionResponse();
        if (!projectPermissionService.isAdmin() && accessibleProjectIds.isEmpty()) {
            response.setItems(Collections.<ReviewSubmitterOptionResponse.Item>emptyList());
            return response;
        }
        if (accessibleProjectIds.isEmpty()) {
            response.setItems(Collections.<ReviewSubmitterOptionResponse.Item>emptyList());
            return response;
        }

        QueryWrapper<UserProjectRelEntity> relationWrapper = new QueryWrapper<UserProjectRelEntity>();
        relationWrapper.in("project_id", accessibleProjectIds);
        List<UserProjectRelEntity> relations = userProjectRelMapper.selectList(relationWrapper);
        if (relations == null || relations.isEmpty()) {
            response.setItems(Collections.<ReviewSubmitterOptionResponse.Item>emptyList());
            return response;
        }

        Set<Long> userIds = new LinkedHashSet<Long>();
        for (UserProjectRelEntity relation : relations) {
            if (relation.getUserId() != null) {
                userIds.add(relation.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            response.setItems(Collections.<ReviewSubmitterOptionResponse.Item>emptyList());
            return response;
        }

        QueryWrapper<UserEntity> userWrapper = new QueryWrapper<UserEntity>();
        userWrapper.in("id", userIds)
            .eq("status", "ENABLE")
            .orderByAsc("display_name")
            .orderByAsc("username");
        List<UserEntity> users = userMapper.selectList(userWrapper);

        Map<String, ReviewSubmitterOptionResponse.Item> optionMap =
            new LinkedHashMap<String, ReviewSubmitterOptionResponse.Item>();
        for (UserEntity user : users) {
            if (!StringUtils.hasText(user.getGitlabUsername())) {
                continue;
            }
            String gitlabUsername = user.getGitlabUsername().trim();
            if (optionMap.containsKey(gitlabUsername)) {
                continue;
            }
            ReviewSubmitterOptionResponse.Item item = new ReviewSubmitterOptionResponse.Item();
            item.setGitlabUsername(gitlabUsername);
            item.setOperatorName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername());
            item.setDisplayLabel(buildSubmitterLabel(item.getOperatorName(), gitlabUsername));
            optionMap.put(gitlabUsername, item);
        }
        response.setItems(new ArrayList<ReviewSubmitterOptionResponse.Item>(optionMap.values()));
        return response;
    }
    public ProjectOverviewResponse getProjectStats() {
        List<CodeReviewTaskEntity> tasks = loadScopedTasks();
        Map<Long, CodeReviewEventEntity> eventMap = buildEventMap(tasks);
        Map<Long, CodeReviewResultEntity> resultMap = buildResultMap(tasks);
        Map<Long, ProjectOverviewResponse.Item> statsMap = new HashMap<Long, ProjectOverviewResponse.Item>();
        Map<Long, ScoreAccumulator> scoreMap = new HashMap<Long, ScoreAccumulator>();
        int highRiskTasks = 0;
        for (CodeReviewTaskEntity task : tasks) {
            ProjectOverviewResponse.Item item = statsMap.get(task.getProjectId());
            if (item == null) {
                item = new ProjectOverviewResponse.Item();
                item.setProjectId(task.getProjectId());
                CodeReviewEventEntity event = eventMap.get(task.getEventId());
                item.setProjectName(event != null ? event.getProjectName() : String.valueOf(task.getProjectId()));
                statsMap.put(task.getProjectId(), item);
            }
            item.setTaskCount(item.getTaskCount() + 1);
            if ("SUCCESS".equals(task.getStatus())) {
                item.setSuccessCount(item.getSuccessCount() + 1);
            }
            if ("FAILED".equals(task.getStatus())) {
                item.setFailedCount(item.getFailedCount() + 1);
            }
            item.setLastReviewAt(max(item.getLastReviewAt(), chooseTime(task)));
            CodeReviewResultEntity reviewResult = resultMap.get(task.getId());
            if (isHighRisk(reviewResult)) {
                item.setHighRiskCount(item.getHighRiskCount() + 1);
                highRiskTasks++;
            }
            addScore(scoreMap, task.getProjectId(), reviewResult == null ? null : reviewResult.getFinalScore());
        }
        List<ProjectOverviewResponse.Item> items = new ArrayList<ProjectOverviewResponse.Item>(statsMap.values());
        for (ProjectOverviewResponse.Item item : items) {
            item.setAverageFinalScore(resolveAverage(scoreMap.get(item.getProjectId())));
        }
        Collections.sort(items, new Comparator<ProjectOverviewResponse.Item>() {
            @Override
            public int compare(ProjectOverviewResponse.Item left, ProjectOverviewResponse.Item right) {
                return Integer.compare(right.getTaskCount(), left.getTaskCount());
            }
        });
        ProjectOverviewResponse response = new ProjectOverviewResponse();
        response.setProjects(items);
        response.setTotalProjects(items.size());
        response.setTotalTasks(tasks.size());
        response.setHighRiskTasks(highRiskTasks);
        return response;
    }

    public DeveloperStatsResponse getDeveloperStats() {
        List<CodeReviewTaskEntity> tasks = loadScopedTasks();
        Map<Long, CodeReviewEventEntity> eventMap = buildEventMap(tasks);
        Map<Long, CodeReviewResultEntity> resultMap = buildResultMap(tasks);
        Map<String, DeveloperStatsResponse.Item> statsMap = new HashMap<String, DeveloperStatsResponse.Item>();
        Map<String, ScoreAccumulator> scoreMap = new HashMap<String, ScoreAccumulator>();
        int highRiskTasks = 0;
        for (CodeReviewTaskEntity task : tasks) {
            CodeReviewEventEntity event = eventMap.get(task.getEventId());
            String developerId = event != null && StringUtils.hasText(event.getOperatorId()) ? event.getOperatorId() : "unknown";
            DeveloperStatsResponse.Item item = statsMap.get(developerId);
            if (item == null) {
                item = new DeveloperStatsResponse.Item();
                item.setDeveloperId(developerId);
                item.setDeveloperName(event != null && StringUtils.hasText(event.getOperatorName()) ? event.getOperatorName() : "unknown");
                statsMap.put(developerId, item);
            }
            item.setReviewCount(item.getReviewCount() + 1);
            if ("SUCCESS".equals(task.getStatus())) {
                item.setSuccessCount(item.getSuccessCount() + 1);
            }
            item.setLastActiveAt(max(item.getLastActiveAt(), chooseTime(task)));
            CodeReviewResultEntity reviewResult = resultMap.get(task.getId());
            if (isHighRisk(reviewResult)) {
                item.setHighRiskCount(item.getHighRiskCount() + 1);
                highRiskTasks++;
            }
            addScore(scoreMap, developerId, reviewResult == null ? null : reviewResult.getFinalScore());
        }
        List<DeveloperStatsResponse.Item> items = new ArrayList<DeveloperStatsResponse.Item>(statsMap.values());
        for (DeveloperStatsResponse.Item item : items) {
            item.setAverageFinalScore(resolveAverage(scoreMap.get(item.getDeveloperId())));
        }
        Collections.sort(items, new Comparator<DeveloperStatsResponse.Item>() {
            @Override
            public int compare(DeveloperStatsResponse.Item left, DeveloperStatsResponse.Item right) {
                return Integer.compare(right.getReviewCount(), left.getReviewCount());
            }
        });
        DeveloperStatsResponse response = new DeveloperStatsResponse();
        response.setDevelopers(items);
        response.setTotalDevelopers(items.size());
        response.setTotalTasks(tasks.size());
        response.setHighRiskTasks(highRiskTasks);
        return response;
    }

    public ScoreStatsResponse getScoreStats(String startDate, String endDate) {
        Date start = parseDate(startDate);
        Date endExclusive = parseEndExclusive(endDate);
        List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
        if (!projectPermissionService.isAdmin() && accessibleProjectIds.isEmpty()) {
            ScoreStatsResponse response = new ScoreStatsResponse();
            response.setStartDate(startDate);
            response.setEndDate(endDate);
            response.setReviewCount(0);
            response.setProjects(Collections.<ScoreStatsResponse.ProjectScoreItem>emptyList());
            response.setDevelopers(Collections.<ScoreStatsResponse.DeveloperScoreItem>emptyList());
            return response;
        }
        QueryWrapper<CodeReviewResultEntity> resultWrapper = new QueryWrapper<CodeReviewResultEntity>();
        resultWrapper.ge("created_at", start).lt("created_at", endExclusive).orderByDesc("id");
        List<CodeReviewResultEntity> results = codeReviewResultMapper.selectList(resultWrapper);
        Map<Long, CodeReviewTaskEntity> taskMap = buildTaskMapByIds(results, accessibleProjectIds);
        Map<Long, CodeReviewEventEntity> eventMap = buildEventMap(new ArrayList<CodeReviewTaskEntity>(taskMap.values()));
        ScoreAccumulator overall = new ScoreAccumulator();
        Map<Long, ScoreStatsResponse.ProjectScoreItem> projectMap = new HashMap<Long, ScoreStatsResponse.ProjectScoreItem>();
        Map<String, ScoreStatsResponse.DeveloperScoreItem> developerMap = new HashMap<String, ScoreStatsResponse.DeveloperScoreItem>();
        Map<Long, ScoreAccumulator> projectScores = new HashMap<Long, ScoreAccumulator>();
        Map<String, ScoreAccumulator> developerScores = new HashMap<String, ScoreAccumulator>();
        for (CodeReviewResultEntity result : results) {
            CodeReviewTaskEntity task = taskMap.get(result.getTaskId());
            if (task == null) {
                continue;
            }
            addScore(overall, result.getFinalScore());
            CodeReviewEventEntity event = eventMap.get(task.getEventId());
            Long projectId = task.getProjectId();
            ScoreStatsResponse.ProjectScoreItem projectItem = projectMap.get(projectId);
            if (projectItem == null) {
                projectItem = new ScoreStatsResponse.ProjectScoreItem();
                projectItem.setProjectId(projectId);
                projectItem.setProjectName(event != null ? event.getProjectName() : String.valueOf(projectId));
                projectMap.put(projectId, projectItem);
            }
            projectItem.setReviewCount(projectItem.getReviewCount() + 1);
            addScore(projectScores, projectId, result.getFinalScore());
            String developerId = event != null && StringUtils.hasText(event.getOperatorId()) ? event.getOperatorId() : "unknown";
            ScoreStatsResponse.DeveloperScoreItem developerItem = developerMap.get(developerId);
            if (developerItem == null) {
                developerItem = new ScoreStatsResponse.DeveloperScoreItem();
                developerItem.setDeveloperId(developerId);
                developerItem.setDeveloperName(event != null && StringUtils.hasText(event.getOperatorName()) ? event.getOperatorName() : "unknown");
                developerMap.put(developerId, developerItem);
            }
            developerItem.setReviewCount(developerItem.getReviewCount() + 1);
            addScore(developerScores, developerId, result.getFinalScore());
        }
        List<ScoreStatsResponse.ProjectScoreItem> projectItems = new ArrayList<ScoreStatsResponse.ProjectScoreItem>(projectMap.values());
        for (ScoreStatsResponse.ProjectScoreItem item : projectItems) {
            item.setAverageFinalScore(resolveAverage(projectScores.get(item.getProjectId())));
        }
        Collections.sort(projectItems, new Comparator<ScoreStatsResponse.ProjectScoreItem>() {
            @Override
            public int compare(ScoreStatsResponse.ProjectScoreItem left, ScoreStatsResponse.ProjectScoreItem right) {
                return compareAverage(right.getAverageFinalScore(), left.getAverageFinalScore());
            }
        });
        List<ScoreStatsResponse.DeveloperScoreItem> developerItems = new ArrayList<ScoreStatsResponse.DeveloperScoreItem>(developerMap.values());
        for (ScoreStatsResponse.DeveloperScoreItem item : developerItems) {
            item.setAverageFinalScore(resolveAverage(developerScores.get(item.getDeveloperId())));
        }
        Collections.sort(developerItems, new Comparator<ScoreStatsResponse.DeveloperScoreItem>() {
            @Override
            public int compare(ScoreStatsResponse.DeveloperScoreItem left, ScoreStatsResponse.DeveloperScoreItem right) {
                return compareAverage(right.getAverageFinalScore(), left.getAverageFinalScore());
            }
        });
        ScoreStatsResponse response = new ScoreStatsResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setReviewCount(overall.count);
        response.setAverageFinalScore(resolveAverage(overall));
        response.setProjects(projectItems);
        response.setDevelopers(developerItems);
        return response;
    }

    private Set<Long> resolveTaskIdsForCrossTableFilters(ReviewTaskQueryRequest request) {
        Set<Long> ids = null;
        if (StringUtils.hasText(request.getRiskLevel())) {
            QueryWrapper<CodeReviewResultEntity> wrapper = new QueryWrapper<CodeReviewResultEntity>();
            wrapper.eq("risk_level", request.getRiskLevel().trim());
            List<CodeReviewResultEntity> results = codeReviewResultMapper.selectList(wrapper);
            Set<Long> resultTaskIds = new HashSet<Long>();
            for (CodeReviewResultEntity result : results) {
                if (result.getTaskId() != null) {
                    resultTaskIds.add(result.getTaskId());
                }
            }
            ids = resultTaskIds;
        }
        if (StringUtils.hasText(request.getGitlabUsername())) {
            String keyword = request.getGitlabUsername().trim();
            Set<Long> gitlabUserTaskIds = resolveTaskIdsByEventFilter("operator_id", keyword, false);
            gitlabUserTaskIds.addAll(resolveTaskIdsByEventFilter("operator_name", keyword, true));
            if (ids == null) {
                ids = gitlabUserTaskIds;
            } else {
                ids.retainAll(gitlabUserTaskIds);
            }
        }
        if (StringUtils.hasText(request.getOperatorName())) {
            Set<Long> eventTaskIds = resolveTaskIdsByEventFilter("operator_name", request.getOperatorName().trim(), true);
            if (ids == null) {
                ids = eventTaskIds;
            } else {
                ids.retainAll(eventTaskIds);
            }
        }
        return ids;
    }

    private Set<Long> resolveTaskIdsByEventFilter(String column, String value, boolean useLike) {
        QueryWrapper<CodeReviewEventEntity> eventWrapper = new QueryWrapper<CodeReviewEventEntity>();
        if (useLike) {
            eventWrapper.like(column, value);
        } else {
            eventWrapper.eq(column, value);
        }
        List<CodeReviewEventEntity> events = codeReviewEventMapper.selectList(eventWrapper);
        Set<Long> eventIds = new HashSet<Long>();
        for (CodeReviewEventEntity event : events) {
            if (event.getId() != null) {
                eventIds.add(event.getId());
            }
        }
        Set<Long> taskIds = new HashSet<Long>();
        if (eventIds.isEmpty()) {
            return taskIds;
        }
        QueryWrapper<CodeReviewTaskEntity> taskWrapper = new QueryWrapper<CodeReviewTaskEntity>();
        taskWrapper.in("event_id", eventIds);
        List<CodeReviewTaskEntity> tasks = codeReviewTaskMapper.selectList(taskWrapper);
        for (CodeReviewTaskEntity task : tasks) {
            if (task.getId() != null) {
                taskIds.add(task.getId());
            }
        }
        return taskIds;
    }

    private String resolveSubmitBranch(CodeReviewEventEntity event) {
        if (event == null) {
            return null;
        }
        if (StringUtils.hasText(event.getSubmitBranch())) {
            return event.getSubmitBranch().trim();
        }
        if (!StringUtils.hasText(event.getPayloadJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(event.getPayloadJson());
            JsonNode objectAttributes = root.path("object_attributes");
            if (objectAttributes.isObject()) {
                String sourceBranch = objectAttributes.path("source_branch").asText(null);
                if (StringUtils.hasText(sourceBranch)) {
                    return sourceBranch.trim();
                }
            }
            String ref = root.path("ref").asText(null);
            if (!StringUtils.hasText(ref)) {
                return null;
            }
            String normalized = ref.trim();
            String prefix = "refs/heads/";
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
            return normalized;
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildSubmitterLabel(String operatorName, String gitlabUsername) {
        if (!StringUtils.hasText(operatorName) || operatorName.trim().equals(gitlabUsername)) {
            return gitlabUsername;
        }
        return operatorName.trim() + " (" + gitlabUsername + ")";
    }

    private ReviewTaskPageResponse emptyReviewTaskPage(long pageNo, long pageSize) {
        ReviewTaskPageResponse empty = new ReviewTaskPageResponse();
        empty.setPageNo(pageNo);
        empty.setPageSize(pageSize);
        empty.setTotal(0);
        empty.setRecords(Collections.<ReviewTaskPageResponse.Item>emptyList());
        return empty;
    }

    private ReviewTaskPageResponse.Item buildReviewTaskItem(CodeReviewTaskEntity task, CodeReviewEventEntity event, CodeReviewResultEntity reviewResult) {
        ReviewTaskPageResponse.Item item = new ReviewTaskPageResponse.Item();
        item.setTaskId(task.getId());
        item.setProjectId(task.getProjectId());
        item.setProjectName(event != null ? event.getProjectName() : null);
        item.setTargetId(task.getTargetId());
        item.setTargetTitle(task.getTargetTitle());
        item.setSubmitBranch(resolveSubmitBranch(event));
        item.setStatus(task.getStatus());
        item.setFixStatus(task.getFixStatus());
        item.setRetryCount(task.getRetryCount());
        item.setOperatorName(event != null ? event.getOperatorName() : null);
        item.setRiskLevel(reviewResult != null ? reviewResult.getRiskLevel() : null);
        item.setFinalScore(reviewResult != null ? reviewResult.getFinalScore() : null);
        item.setSummary(reviewResult != null ? reviewResult.getSummary() : null);
        item.setCreatedAt(task.getCreatedAt());
        item.setFinishedAt(task.getFinishedAt());
        return item;
    }

    private CodeReviewResultEntity findResultByTaskId(Long taskId) {
        QueryWrapper<CodeReviewResultEntity> wrapper = new QueryWrapper<CodeReviewResultEntity>();
        wrapper.eq("task_id", taskId).orderByDesc("id").last("limit 1");
        return codeReviewResultMapper.selectOne(wrapper);
    }

    private UserEntity resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    private String resolveUserDisplayName(UserEntity user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName();
        }
        return user.getUsername();
    }

    private Map<Long, CodeReviewEventEntity> buildEventMap(List<CodeReviewTaskEntity> tasks) {
        Set<Long> eventIds = new HashSet<Long>();
        for (CodeReviewTaskEntity task : tasks) {
            if (task.getEventId() != null) {
                eventIds.add(task.getEventId());
            }
        }
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<CodeReviewEventEntity> wrapper = new QueryWrapper<CodeReviewEventEntity>();
        wrapper.in("id", eventIds);
        List<CodeReviewEventEntity> events = codeReviewEventMapper.selectList(wrapper);
        Map<Long, CodeReviewEventEntity> eventMap = new HashMap<Long, CodeReviewEventEntity>();
        for (CodeReviewEventEntity event : events) {
            eventMap.put(event.getId(), event);
        }
        return eventMap;
    }

    private Map<Long, CodeReviewResultEntity> buildResultMap(List<CodeReviewTaskEntity> tasks) {
        Set<Long> taskIds = new HashSet<Long>();
        for (CodeReviewTaskEntity task : tasks) {
            if (task.getId() != null) {
                taskIds.add(task.getId());
            }
        }
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<CodeReviewResultEntity> wrapper = new QueryWrapper<CodeReviewResultEntity>();
        wrapper.in("task_id", taskIds).orderByDesc("id");
        List<CodeReviewResultEntity> results = codeReviewResultMapper.selectList(wrapper);
        Map<Long, CodeReviewResultEntity> resultMap = new HashMap<Long, CodeReviewResultEntity>();
        for (CodeReviewResultEntity result : results) {
            if (!resultMap.containsKey(result.getTaskId())) {
                resultMap.put(result.getTaskId(), result);
            }
        }
        return resultMap;
    }

    private Map<Long, CodeReviewTaskEntity> buildTaskMapByIds(List<CodeReviewResultEntity> results, List<Long> accessibleProjectIds) {
        Set<Long> taskIds = new HashSet<Long>();
        for (CodeReviewResultEntity result : results) {
            if (result.getTaskId() != null) {
                taskIds.add(result.getTaskId());
            }
        }
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        wrapper.in("id", taskIds);
        if (!projectPermissionService.isAdmin()) {
            if (accessibleProjectIds == null || accessibleProjectIds.isEmpty()) {
                return Collections.emptyMap();
            }
            wrapper.in("project_id", accessibleProjectIds);
        }
        List<CodeReviewTaskEntity> tasks = codeReviewTaskMapper.selectList(wrapper);
        Map<Long, CodeReviewTaskEntity> taskMap = new HashMap<Long, CodeReviewTaskEntity>();
        for (CodeReviewTaskEntity task : tasks) {
            taskMap.put(task.getId(), task);
        }
        return taskMap;
    }

    private List<CodeReviewTaskEntity> loadScopedTasks() {
        QueryWrapper<CodeReviewTaskEntity> wrapper = new QueryWrapper<CodeReviewTaskEntity>();
        if (!projectPermissionService.isAdmin()) {
            List<Long> accessibleProjectIds = projectPermissionService.getAccessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in("project_id", accessibleProjectIds);
        }
        return codeReviewTaskMapper.selectList(wrapper);
    }

    private boolean isHighRisk(CodeReviewResultEntity result) {
        if (result == null || !StringUtils.hasText(result.getRiskLevel())) {
            return false;
        }
        return "HIGH".equalsIgnoreCase(result.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(result.getRiskLevel());
    }

    private Date chooseTime(CodeReviewTaskEntity task) {
        return task.getFinishedAt() != null ? task.getFinishedAt() : task.getCreatedAt();
    }

    private Date max(Date left, Date right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.after(right) ? left : right;
    }

    private Date parseDate(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            format.setLenient(false);
            Date date = format.parse(value);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime();
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Date must use yyyy-MM-dd format");
        }
    }

    private Date parseEndExclusive(String value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(parseDate(value));
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }

    private void addScore(Map<Long, ScoreAccumulator> scoreMap, Long key, Integer score) {
        if (key == null) {
            return;
        }
        ScoreAccumulator accumulator = scoreMap.get(key);
        if (accumulator == null) {
            accumulator = new ScoreAccumulator();
            scoreMap.put(key, accumulator);
        }
        addScore(accumulator, score);
    }

    private void addScore(Map<String, ScoreAccumulator> scoreMap, String key, Integer score) {
        ScoreAccumulator accumulator = scoreMap.get(key);
        if (accumulator == null) {
            accumulator = new ScoreAccumulator();
            scoreMap.put(key, accumulator);
        }
        addScore(accumulator, score);
    }

    private void addScore(ScoreAccumulator accumulator, Integer score) {
        if (accumulator == null || score == null) {
            return;
        }
        accumulator.total += score;
        accumulator.count++;
    }

    private Double resolveAverage(ScoreAccumulator accumulator) {
        if (accumulator == null || accumulator.count == 0) {
            return null;
        }
        return BigDecimal.valueOf((double) accumulator.total / accumulator.count).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private int compareAverage(Double left, Double right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return Double.compare(left, right);
    }

    private static class ScoreAccumulator {
        private int total;
        private int count;
    }
}
