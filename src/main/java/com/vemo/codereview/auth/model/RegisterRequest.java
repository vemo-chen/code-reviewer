package com.vemo.codereview.auth.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    private String username;
    private String displayName;
    private String email;
    private String password;
    private String gitlabUsername;
}
