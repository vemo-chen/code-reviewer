package com.vemo.codereview.project.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectPageResponse {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<ProjectDetailResponse> records = new ArrayList<ProjectDetailResponse>();
}
