package com.vemo.codereview.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.auth.model.AuthSession;
import com.vemo.codereview.auth.model.ChangePasswordRequest;
import com.vemo.codereview.auth.model.LoginRequest;
import com.vemo.codereview.auth.model.LoginResponse;
import com.vemo.codereview.auth.model.RegisterRequest;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import java.util.Date;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;
    private final AuthTokenService authTokenService;

    public AuthService(
        UserMapper userMapper,
        PasswordHashService passwordHashService,
        AuthTokenService authTokenService) {
        this.userMapper = userMapper;
        this.passwordHashService = passwordHashService;
        this.authTokenService = authTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "Username and password are required");
        }

        UserEntity user = findByUsername(request.getUsername().trim());
        if (user == null || !passwordHashService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new DomainException("AUTH_INVALID", "Username or password is invalid");
        }
        if (!"ENABLE".equalsIgnoreCase(user.getStatus())) {
            throw new DomainException("AUTH_FORBIDDEN", "User is disabled");
        }

        String token = authTokenService.createToken(user);
        return buildLoginResponse(token, user);
    }

    public LoginResponse register(RegisterRequest request) {
        if (request == null
            || !StringUtils.hasText(request.getUsername())
            || !StringUtils.hasText(request.getDisplayName())
            || !StringUtils.hasText(request.getPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "Username, display name and password are required");
        }

        String username = request.getUsername().trim();
        String displayName = request.getDisplayName().trim();
        String password = request.getPassword().trim();
        if (password.length() < 4) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "Password must be at least 4 characters");
        }
        if (findByUsername(username) != null) {
            throw new DomainException("AUTH_USERNAME_DUPLICATE", "用户名已存在");
        }

        Date now = new Date();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setGitlabUsername(normalizeGitlabUsername(request.getGitlabUsername()));
        user.setPasswordHash(passwordHashService.sha256(password));
        user.setRole("USER");
        user.setStatus("ENABLE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new DomainException("AUTH_USERNAME_DUPLICATE", "用户名已存在");
        }

        String token = authTokenService.createToken(user);
        return buildLoginResponse(token, user);
    }

    public LoginResponse currentUser(String authorizationHeader) {
        AuthSession session = requireSession(authorizationHeader);
        LoginResponse response = new LoginResponse();
        response.setToken(extractBearerToken(authorizationHeader));
        response.setUserId(session.getUserId());
        response.setUsername(session.getUsername());
        response.setDisplayName(session.getDisplayName());
        response.setRole(session.getRole());
        return response;
    }

    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return;
        }
        authTokenService.removeToken(token);
    }

    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        AuthSession session = requireSession(authorizationHeader);
        if (request == null
            || !StringUtils.hasText(request.getCurrentPassword())
            || !StringUtils.hasText(request.getNewPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "Current password and new password are required");
        }
        String newPassword = request.getNewPassword().trim();
        if (newPassword.length() < 4) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "New password must be at least 4 characters");
        }

        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Current user does not exist");
        }
        if (!passwordHashService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "Current password is incorrect");
        }
        if (passwordHashService.matches(newPassword, user.getPasswordHash())) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "New password cannot be the same as current password");
        }

        user.setPasswordHash(passwordHashService.sha256(newPassword));
        userMapper.updateById(user);
        authTokenService.removeToken(extractBearerToken(authorizationHeader));
    }

    private UserEntity findByUsername(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("username", username).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private String normalizeGitlabUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            return null;
        }
        return gitlabUsername.trim();
    }

    private LoginResponse buildLoginResponse(String token, UserEntity user) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setRole(user.getRole());
        return response;
    }

    private AuthSession requireSession(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Authorization token is missing");
        }
        AuthSession session = authTokenService.getSession(token);
        if (session == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Authorization token is invalid");
        }
        return session;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return trimmed;
    }
}
