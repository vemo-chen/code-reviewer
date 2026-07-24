package com.vemo.codereview.user.model;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDetailResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String employeeCode;
    private Long ssoUserId;
    private String authSource;
    private Boolean passwordInitialized;
    private String gitlabUsername;
    private String role;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
