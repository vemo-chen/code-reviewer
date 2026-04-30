package com.vemo.codereview.user.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectUsersResponse {

    private Long projectId;
    private List<Item> users;

    @Getter
    @Setter
    public static class Item {

        private Long id;
        private String username;
        private String displayName;
        private String role;
        private String status;
    }
}
