package com.vemo.codereview.platform.gitlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitLabChangesPayload {

    private Long id;
    private Long iid;
    private String title;
    private List<Change> changes = new ArrayList<Change>();

    @Getter
    @Setter
    public static class Change {
        @JsonProperty("old_path")
        private String oldPath;

        @JsonProperty("new_path")
        private String newPath;

        private String diff;

        @JsonProperty("new_file")
        private Boolean newFile;

        @JsonProperty("deleted_file")
        private Boolean deletedFile;

        @JsonProperty("renamed_file")
        private Boolean renamedFile;
    }
}
