package com.vemo.codereview.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.report.entity.DailyReportRecordEntity;
import com.vemo.codereview.report.mapper.DailyReportStoreMapper;
import com.vemo.codereview.report.model.DailyReportSummary;
import com.vemo.codereview.report.service.DailyReportAggregationService;
import com.vemo.codereview.review.entity.CodeReviewEventEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewEventStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:daily-report-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class DailyReportAggregationServiceTest {

    @Autowired
    private DailyReportAggregationService dailyReportAggregationService;

    @Autowired
    private DailyReportStoreMapper dailyReportStoreMapper;

    @Autowired
    private ReviewEventStoreMapper codeReviewEventMapper;

    @Autowired
    private ReviewTaskStoreMapper codeReviewTaskMapper;

    @Autowired
    private ReviewResultStoreMapper codeReviewResultMapper;

    @BeforeEach
    void setUpData() {
        dailyReportStoreMapper.delete(null);
        codeReviewResultMapper.delete(null);
        codeReviewTaskMapper.delete(null);
        codeReviewEventMapper.delete(null);

        Date now = new Date();

        CodeReviewEventEntity event = new CodeReviewEventEntity();
        event.setSourcePlatform("gitlab");
        event.setEventType("merge_request");
        event.setProjectId(1001L);
        event.setProjectName("code-reviewer");
        event.setObjectId("501");
        event.setObjectType("merge_request");
        event.setOperatorId("u001");
        event.setOperatorName("alice");
        event.setIdempotentKey("daily-report-1001-501");
        event.setPayloadJson("{}");
        event.setStatus("PENDING");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        codeReviewEventMapper.insert(event);

        CodeReviewTaskEntity task = new CodeReviewTaskEntity();
        task.setEventId(event.getId());
        task.setTaskType("MR_REVIEW");
        task.setSourcePlatform("gitlab");
        task.setProjectId(1001L);
        task.setTargetId("7");
        task.setTargetTitle("Add daily report");
        task.setStatus("SUCCESS");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        codeReviewTaskMapper.insert(task);

        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setTaskId(task.getId());
        result.setProviderName("openai-compatible");
        result.setModelName("gpt-4o-mini");
        result.setRiskLevel("HIGH");
        result.setSummary("Found one important issue");
        result.setAdvice("Fix before merge");
        result.setCreatedAt(now);
        codeReviewResultMapper.insert(result);
    }

    @Test
    void shouldAggregateDailyReviewDataAndPersistRecord() {
        List<DailyReportSummary> summaries = dailyReportAggregationService.aggregate(new Date());

        assertFalse(summaries.isEmpty());
        DailyReportSummary summary = summaries.get(0);
        assertEquals(Long.valueOf(1001L), summary.getProjectId());
        assertEquals("alice", summary.getDeveloperName());
        assertEquals(1, summary.getCommitCount());
        assertEquals(1, summary.getMrCount());
        assertEquals(1, summary.getReviewCount());
        assertEquals(1, summary.getHighRiskCount());
        assertTrue(summary.getSummary().contains("\u4ea7\u751f 1 \u6b21\u5ba1\u67e5\u8bb0\u5f55"));

        DailyReportRecordEntity record = dailyReportStoreMapper.selectById(1L);
        assertEquals(Integer.valueOf(1), record.getReviewCount());
        assertEquals(Integer.valueOf(1), record.getHighRiskCount());
    }
}