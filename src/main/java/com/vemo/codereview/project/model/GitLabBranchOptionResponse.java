package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabBranchOptionResponse {

    private String name;
    private Boolean defaultBranch;
    private Boolean protectedBranch;
}
