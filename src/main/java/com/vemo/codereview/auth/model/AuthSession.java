package com.vemo.codereview.auth.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthSession {

    private Long userId;
    private String username;
    private String displayName;
    private String role;
}
