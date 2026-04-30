package com.vemo.codereview.dashboard.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchRetryRequest {

    private List<Long> taskIds;
}
