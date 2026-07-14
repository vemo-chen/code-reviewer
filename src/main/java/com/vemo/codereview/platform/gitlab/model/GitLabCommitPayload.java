package com.vemo.codereview.platform.gitlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabCommitPayload {

    private String id;
    private String title;
    private String message;
    @JsonProperty("parent_ids")
    private List<String> parentIds;

    @JsonProperty("author_name")
    private String authorName;

    @JsonProperty("committed_date")
    private String committedDate;

    @JsonProperty("web_url")
    private String webUrl;
}
