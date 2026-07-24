package com.vemo.codereview.auth.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SsoEmployeeProfile {

    private Long ssoUserId;
    private String employeeCode;
    private String name;
    private String email;
}
