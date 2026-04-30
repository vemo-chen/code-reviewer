package com.vemo.codereview.common.model;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setSuccess(true);
        response.setCode("OK");
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
