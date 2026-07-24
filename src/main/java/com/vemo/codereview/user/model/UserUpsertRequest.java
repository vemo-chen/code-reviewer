package com.vemo.codereview.user.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpsertRequest {

    private String username;
    private String displayName;
    private String email;
    private String gitlabUsername;
    private String password;
    private String role;
    private String status;
}
