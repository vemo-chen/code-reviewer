package com.vemo.codereview.platform.gitlab.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabCommitNoteRequest {

    private String note;

    public GitLabCommitNoteRequest() {
    }

    public GitLabCommitNoteRequest(String note) {
        this.note = note;
    }
}
