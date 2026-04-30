package com.vemo.codereview.dashboard.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScoreStatsResponse {

    private String startDate;
    private String endDate;
    private int reviewCount;
    private Double averageFinalScore;
    private List<ProjectScoreItem> projects = new ArrayList<ProjectScoreItem>();
    private List<DeveloperScoreItem> developers = new ArrayList<DeveloperScoreItem>();

    @Getter
    @Setter
    public static class ProjectScoreItem {
        private Long projectId;
        private String projectName;
        private int reviewCount;
        private Double averageFinalScore;
    }

    @Getter
    @Setter
    public static class DeveloperScoreItem {
        private String developerId;
        private String developerName;
        private int reviewCount;
        private Double averageFinalScore;
    }
}
