package com.vemo.codereview.project.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabProjectTestRequest {

    private String gitlabProjectUrl;
    private String gitlabWebhookToken;
}
