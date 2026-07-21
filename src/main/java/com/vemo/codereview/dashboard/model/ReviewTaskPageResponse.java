package com.vemo.codereview.dashboard.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewTaskPageResponse {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<Item> records = new ArrayList<Item>();

    @Getter
    @Setter
    public static class Item {
        private Long taskId;
        private Long projectId;
        private String projectName;
        private String targetId;
        private String targetTitle;
        private String submitBranch;
        private String status;
        private String fixStatus;
        private Integer retryCount;
        private String operatorName;
        private String riskLevel;
        private Integer finalScore;
        private String summary;
        private Date submitTime;
        private Date createdAt;
        private Date updatedAt;
        private Date finishedAt;
    }
}
