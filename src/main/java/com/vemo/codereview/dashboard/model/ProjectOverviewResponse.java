package com.vemo.codereview.dashboard.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectOverviewResponse {

    private int totalProjects;
    private int totalTasks;
    private int highRiskTasks;
    private List<Item> projects = new ArrayList<Item>();

    @Getter
    @Setter
    public static class Item {
        private Long projectId;
        private String projectName;
        private int taskCount;
        private int successCount;
        private int failedCount;
        private int highRiskCount;
        private Double averageFinalScore;
        private Date lastReviewAt;
    }
}