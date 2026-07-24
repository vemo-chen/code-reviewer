package com.vemo.codereview.auth.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SsoLoginRequest {

    private String employeeCode;
    private String password;
}
