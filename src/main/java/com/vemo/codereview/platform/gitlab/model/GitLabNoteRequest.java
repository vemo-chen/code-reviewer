package com.vemo.codereview.platform.gitlab.model;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class GitLabNoteRequest {

    private String body;

    public GitLabNoteRequest() {
    }

    public GitLabNoteRequest(String body) {
        this.body = body;
    }
}
