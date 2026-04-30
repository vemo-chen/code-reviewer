package com.vemo.codereview.review.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewFixFlowResponse {

    private List<Item> records;

    @Getter
    @Setter
    public static class Item {

        private Long id;
        private Long taskId;
        private String fromStatus;
        private String toStatus;
        private Long operatorUserId;
        private String operatorName;
        private String comment;
        private Date createdAt;
    }
}
