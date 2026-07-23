package com.vemo.codereview.auth.controller;

import com.vemo.codereview.auth.model.LoginRequest;
import com.vemo.codereview.auth.model.LoginResponse;
import com.vemo.codereview.auth.model.ChangePasswordRequest;
import com.vemo.codereview.auth.model.RegisterRequest;
import com.vemo.codereview.auth.service.AuthService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
        } catch (DomainException ex) {
            return ResponseEntity.status(resolveStatus(ex))
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.register(request)));
        } catch (DomainException ex) {
            return ResponseEntity.status(resolveStatus(ex))
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me(
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.currentUser(authorization)));
        } catch (DomainException ex) {
            return ResponseEntity.status(resolveStatus(ex))
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.ok(ApiResponse.success("logout"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(authorization, request);
            return ResponseEntity.ok(ApiResponse.success("password_changed"));
        } catch (DomainException ex) {
            return ResponseEntity.status(resolveStatus(ex))
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
        }
    }

    private HttpStatus resolveStatus(DomainException ex) {
        if ("AUTH_UNAUTHORIZED".equals(ex.getCode()) || "AUTH_INVALID".equals(ex.getCode())) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("AUTH_FORBIDDEN".equals(ex.getCode())) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
