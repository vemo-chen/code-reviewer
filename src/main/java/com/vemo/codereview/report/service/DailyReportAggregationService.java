package com.vemo.codereview.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.report.entity.DailyReportRecordEntity;
import com.vemo.codereview.report.mapper.DailyReportStoreMapper;
import com.vemo.codereview.report.model.DailyReportSummary;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DailyReportAggregationService {

    private final ReviewTaskStoreMapper codeReviewTaskMapper;
    private final ReviewEventStoreMapper codeReviewEventMapper;
    private final ReviewResultStoreMapper codeReviewResultMapper;
    private final DailyReportStoreMapper DailyReportStoreMapper;

    public DailyReportAggregationService(
        ReviewTaskStoreMapper codeReviewTaskMapper,
        ReviewEventStoreMapper codeReviewEventMapper,
        ReviewResultStoreMapper codeReviewResultMapper,
        DailyReportStoreMapper DailyReportStoreMapper) {
        this.codeReviewTaskMapper = codeReviewTaskMapper;
        this.codeReviewEventMapper = codeReviewEventMapper;
        this.codeReviewResultMapper = codeReviewResultMapper;
        this.DailyReportStoreMapper = DailyReportStoreMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DailyReportSummary> aggregate(Date reportDate) {
        Date start = startOfDay(reportDate);
        Date end = endOfDay(reportDate);

        QueryWrapper<CodeReviewTaskEntity> taskWrapper = new QueryWrapper<CodeReviewTaskEntity>();
        taskWrapper.ge("created_at", start).le("created_at", end);
        List<CodeReviewTaskEntity> tasks = codeReviewTaskMapper.selectList(taskWrapper);
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CodeReviewEventEntity> eventMap = loadEventMap(tasks);
        Map<Long, CodeReviewResultEntity> resultMap = loadResultMap(tasks);
        Map<String, DailyReportSummary> summaryMap = new HashMap<String, DailyReportSummary>();

        for (CodeReviewTaskEntity task : tasks) {
            CodeReviewEventEntity event = eventMap.get(task.getEventId());
            String developerId = event != null && StringUtils.hasText(event.getOperatorId())
                ? event.getOperatorId() : "unknown";
            String key = task.getProjectId() + "::" + developerId;

            DailyReportSummary summary = summaryMap.get(key);
            if (summary == null) {
                summary = new DailyReportSummary();
                summary.setReportDate(start);
                summary.setProjectId(task.getProjectId());
                summary.setProjectName(event != null ? event.getProjectName() : String.valueOf(task.getProjectId()));
                summary.setDeveloperId(developerId);
                summary.setDeveloperName(event != null && StringUtils.hasText(event.getOperatorName())
                    ? event.getOperatorName() : "unknown");
                summaryMap.put(key, summary);
            }

            summary.setCommitCount(summary.getCommitCount() + 1);
            if ("merge_request".equalsIgnoreCase(event != null ? event.getEventType() : null)) {
                summary.setMrCount(summary.getMrCount() + 1);
            }
            summary.setReviewCount(summary.getReviewCount() + 1);

            CodeReviewResultEntity result = resultMap.get(task.getId());
            if (result != null && isHighRisk(result.getRiskLevel())) {
                summary.setHighRiskCount(summary.getHighRiskCount() + 1);
            }
        }

        List<DailyReportSummary> summaries = new ArrayList<DailyReportSummary>(summaryMap.values());
        for (DailyReportSummary summary : summaries) {
            summary.setSummary(buildSummary(summary));
            upsertRecord(summary);
        }
        return summaries;
    }

    private Map<Long, CodeReviewEventEntity> loadEventMap(List<CodeReviewTaskEntity> tasks) {
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
        Map<Long, CodeReviewEventEntity> result = new HashMap<Long, CodeReviewEventEntity>();
        for (CodeReviewEventEntity event : events) {
            result.put(event.getId(), event);
        }
        return result;
    }

    private Map<Long, CodeReviewResultEntity> loadResultMap(List<CodeReviewTaskEntity> tasks) {
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

    private void upsertRecord(DailyReportSummary summary) {
        QueryWrapper<DailyReportRecordEntity> wrapper = new QueryWrapper<DailyReportRecordEntity>();
        wrapper.eq("report_date", new java.sql.Date(summary.getReportDate().getTime()))
            .eq("project_id", summary.getProjectId())
            .eq("developer_id", summary.getDeveloperId());
        DailyReportRecordEntity entity = DailyReportStoreMapper.selectOne(wrapper);
        if (entity == null) {
            entity = new DailyReportRecordEntity();
            entity.setReportDate(summary.getReportDate());
            entity.setProjectId(summary.getProjectId());
            entity.setDeveloperId(summary.getDeveloperId());
            entity.setCreatedAt(new Date());
            DailyReportStoreMapper.insert(fillEntity(entity, summary));
            return;
        }
        DailyReportStoreMapper.updateById(fillEntity(entity, summary));
    }

    private DailyReportRecordEntity fillEntity(DailyReportRecordEntity entity, DailyReportSummary summary) {
        entity.setCommitCount(summary.getCommitCount());
        entity.setMrCount(summary.getMrCount());
        entity.setReviewCount(summary.getReviewCount());
        entity.setHighRiskCount(summary.getHighRiskCount());
        entity.setSummary(summary.getSummary());
        return entity;
    }

    private String buildSummary(DailyReportSummary summary) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.format(summary.getReportDate())
            + "\uFF0C"
            + summary.getDeveloperName()
            + " \u5728\u9879\u76ee "
            + summary.getProjectName()
            + " \u4ea7\u751f "
            + summary.getReviewCount()
            + " \u6b21\u5ba1\u67e5\u8bb0\u5f55\uFF0C\u6d89\u53ca "
            + summary.getMrCount()
            + " \u4e2a MR\uFF0C\u53d1\u73b0 "
            + summary.getHighRiskCount()
            + " \u4e2a\u9ad8\u98ce\u9669\u95ee\u9898\u3002";
    }

    private boolean isHighRisk(String riskLevel) {
        return "HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel);
    }

    private Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Date endOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startOfDay(date));
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        return calendar.getTime();
    }
}
