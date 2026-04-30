package com.vemo.codereview.dashboard.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchRetryResponse {

    private Integer total;
    private Integer successCount;
    private Integer failedCount;
    private List<Item> results;

    @Getter
    @Setter
    public static class Item {

        private Long taskId;
        private Boolean success;
        private String message;
    }
}
