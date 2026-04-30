package com.vemo.codereview.webhook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabWebhookPayload {

    @JsonProperty("object_kind")
    private String objectKind;

    @JsonProperty("event_type")
    private String eventType;

    private User user;
    private Project project;

    @JsonProperty("object_attributes")
    private MergeRequestAttributes objectAttributes;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("user_username")
    private String userUsername;

    private String ref;
    private String before;
    private String after;
    private List<Commit> commits;

    @Getter
    @Setter
    public static class User {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    public static class Project {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    public static class MergeRequestAttributes {
        private Long id;
        private Long iid;
        private String title;
        private String action;

        @JsonProperty("target_branch")
        private String targetBranch;

        @JsonProperty("source_branch")
        private String sourceBranch;

        @JsonProperty("last_commit")
        private LastCommit lastCommit;
    }

    @Getter
    @Setter
    public static class LastCommit {
        private String id;
    }

    @Getter
    @Setter
    public static class Commit {
        private String id;
        private String message;
        private String title;
        private String timestamp;
    }
}
