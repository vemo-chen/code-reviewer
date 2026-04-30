package com.vemo.codereview.common.handler;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        return ResponseEntity.status(resolveStatus(ex.getCode()))
            .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("BAD_REQUEST", ex.getMessage()));
    }

    private HttpStatus resolveStatus(String code) {
        if ("AUTH_UNAUTHORIZED".equals(code) || "AUTH_INVALID".equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("AUTH_FORBIDDEN".equals(code)
            || "PROJECT_ACCESS_DENIED".equals(code)
            || "TASK_ACCESS_DENIED".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
