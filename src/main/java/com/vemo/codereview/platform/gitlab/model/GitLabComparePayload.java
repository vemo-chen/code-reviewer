package com.vemo.codereview.platform.gitlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabComparePayload {

    @JsonProperty("compare_timeout")
    private Boolean compareTimeout;
    @JsonProperty("compare_same_ref")
    private Boolean compareSameRef;
    private List<GitLabChangesPayload.Change> diffs;
    private List<GitLabCommitPayload> commits;
}
