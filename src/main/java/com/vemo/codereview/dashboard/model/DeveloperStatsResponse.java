package com.vemo.codereview.dashboard.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeveloperStatsResponse {

    private int totalDevelopers;
    private int totalTasks;
    private int highRiskTasks;
    private List<Item> developers = new ArrayList<Item>();

    @Getter
    @Setter
    public static class Item {
        private String developerId;
        private String developerName;
        private int reviewCount;
        private int successCount;
        private int highRiskCount;
        private Double averageFinalScore;
        private Date lastActiveAt;
    }
}