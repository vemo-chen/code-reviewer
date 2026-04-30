package com.vemo.codereview.report.service;

import com.vemo.codereview.notify.service.WeComNotificationService;
import com.vemo.codereview.report.model.DailyReportSummary;
import java.text.SimpleDateFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DailyReportPublisher {

    private final WeComNotificationService weComNotificationService;

    public DailyReportPublisher(WeComNotificationService weComNotificationService) {
        this.weComNotificationService = weComNotificationService;
    }

    public boolean publish(List<DailyReportSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }
        return weComNotificationService.notifyDailyReport(buildMarkdown(summaries));
    }

    private String buildMarkdown(List<DailyReportSummary> summaries) {
        DailyReportSummary first = summaries.get(0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        StringBuilder builder = new StringBuilder();
        builder.append("## Daily Code Review Report\n");
        builder.append("> Date: ").append(format.format(first.getReportDate())).append("\n");
        builder.append("> Project count: ").append(summaries.size()).append("\n\n");
        for (DailyReportSummary summary : summaries) {
            builder.append("- Project: ").append(safe(summary.getProjectName()))
                .append(", Developer: ").append(safe(summary.getDeveloperName()))
                .append(", Reviews: ").append(summary.getReviewCount())
                .append(", MRs: ").append(summary.getMrCount())
                .append(", High risk: ").append(summary.getHighRiskCount())
                .append("\n");
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "-" : value.replace("<", "&lt;").replace(">", "&gt;");
    }
}
