package com.vemo.codereview.platform.gitlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabProjectPayload {

    private Long id;
    private String name;

    @JsonProperty("path_with_namespace")
    private String pathWithNamespace;

    @JsonProperty("web_url")
    private String webUrl;
}
