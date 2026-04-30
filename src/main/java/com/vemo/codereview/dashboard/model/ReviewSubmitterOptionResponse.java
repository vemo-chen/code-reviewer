package com.vemo.codereview.dashboard.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSubmitterOptionResponse {

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private String gitlabUsername;
        private String operatorName;
        private String displayLabel;
    }
}
