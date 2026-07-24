package com.vemo.codereview.auth.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SsoEmployeeInfoApiResponse {

    private String code;
    private EmployeeData data;
    private String msg;

    @Getter
    @Setter
    public static class EmployeeData {

        private Long id;
        private Integer enabled;
        private String code;
        private String name;
        private String telephone;
        private String email;
    }
}
