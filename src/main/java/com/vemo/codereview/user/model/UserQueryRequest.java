package com.vemo.codereview.user.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserQueryRequest {

    private long pageNo = 1;
    private long pageSize = 10;
    private String username;
    private String displayName;
    private String gitlabUsername;
    private String role;
    private String status;
}
