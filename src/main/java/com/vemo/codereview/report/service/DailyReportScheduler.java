package com.vemo.codereview.report.service;

import com.vemo.codereview.report.model.DailyReportSummary;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyReportScheduler {

    private final DailyReportAggregationService dailyReportAggregationService;
    private final DailyReportPublisher dailyReportPublisher;

    public DailyReportScheduler(
        DailyReportAggregationService dailyReportAggregationService,
        DailyReportPublisher dailyReportPublisher) {
        this.dailyReportAggregationService = dailyReportAggregationService;
        this.dailyReportPublisher = dailyReportPublisher;
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void publishYesterdayReport() {
        Date reportDate = yesterday();
        List<DailyReportSummary> summaries = dailyReportAggregationService.aggregate(reportDate);
        if (!summaries.isEmpty()) {
            dailyReportPublisher.publish(summaries);
        }
    }

    private Date yesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        return calendar.getTime();
    }
}
