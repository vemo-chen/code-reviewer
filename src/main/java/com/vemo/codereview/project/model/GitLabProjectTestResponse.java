package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabProjectTestResponse {

    private Long gitlabProjectId;
    private String projectName;
    private String pathWithNamespace;
    private String webUrl;
}
