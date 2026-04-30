package com.vemo.codereview.platform.gitlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabBranchPayload {

    private String name;

    @JsonProperty("default")
    private Boolean defaultBranch;

    @JsonProperty("protected")
    private Boolean protectedBranch;
}
