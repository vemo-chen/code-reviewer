package com.vemo.codereview.user.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPageResponse {

    private long pageNo;
    private long pageSize;
    private long total;
    private List<Item> records;

    @Getter
    @Setter
    public static class Item {

        private Long id;
        private String username;
        private String displayName;
        private String gitlabUsername;
        private String role;
        private String status;
        private Integer projectCount;
        private Date createdAt;
        private Date updatedAt;
    }
}
