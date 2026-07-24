package com.vemo.codereview.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.auth.client.SsoAuthClient;
import com.vemo.codereview.auth.model.AuthSession;
import com.vemo.codereview.auth.model.AuthProfileResponse;
import com.vemo.codereview.auth.model.ChangePasswordRequest;
import com.vemo.codereview.auth.model.LoginRequest;
import com.vemo.codereview.auth.model.LoginResponse;
import com.vemo.codereview.auth.model.RegisterRequest;
import com.vemo.codereview.auth.model.SetPasswordRequest;
import com.vemo.codereview.auth.model.SsoEmployeeProfile;
import com.vemo.codereview.auth.model.SsoLoginRequest;
import com.vemo.codereview.auth.model.UpdateGitlabUsernameRequest;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;
    private final AuthTokenService authTokenService;
    private final SsoAuthClient ssoAuthClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        UserMapper userMapper,
        PasswordHashService passwordHashService,
        AuthTokenService authTokenService,
        SsoAuthClient ssoAuthClient) {
        this.userMapper = userMapper;
        this.passwordHashService = passwordHashService;
        this.authTokenService = authTokenService;
        this.ssoAuthClient = ssoAuthClient;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "Username and password are required");
        }

        UserEntity user = findByUsernameOrEmail(request.getUsername().trim());
        if (user == null) {
            throw new DomainException("AUTH_INVALID", "Username or password is invalid");
        }
        if (!"ENABLE".equalsIgnoreCase(user.getStatus())) {
            throw new DomainException("AUTH_FORBIDDEN", "User is disabled");
        }
        if (!isPasswordInitialized(user)) {
            throw new DomainException(
                "AUTH_PASSWORD_NOT_INITIALIZED",
                "当前账号尚未设置平台密码，请先通过公司账号登录后在个人信息中设置平台密码");
        }
        if (!passwordHashService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new DomainException("AUTH_INVALID", "Username or password is invalid");
        }

        String token = authTokenService.createToken(user);
        return buildLoginResponse(token, user);
    }

    public LoginResponse register(RegisterRequest request) {
        if (request == null
            || !StringUtils.hasText(request.getUsername())
            || !StringUtils.hasText(request.getDisplayName())
            || !StringUtils.hasText(request.getEmail())
            || !StringUtils.hasText(request.getPassword())) {
            if (request != null && !StringUtils.hasText(request.getEmail())) {
                throw new DomainException("AUTH_EMAIL_REQUIRED", "邮箱不能为空");
            }
            throw new DomainException("AUTH_PARAM_INVALID", "Username, display name, email and password are required");
        }

        String username = request.getUsername().trim();
        String displayName = request.getDisplayName().trim();
        String email = normalizeEmail(request.getEmail());
        String password = request.getPassword().trim();
        if (password.length() < 4) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "Password must be at least 4 characters");
        }
        if (findByUsername(username) != null) {
            throw new DomainException("AUTH_USERNAME_DUPLICATE", "用户名已存在");
        }
        if (findByEmail(email) != null) {
            throw new DomainException("AUTH_EMAIL_DUPLICATE", "邮箱已存在");
        }

        Date now = new Date();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setGitlabUsername(normalizeGitlabUsername(request.getGitlabUsername()));
        user.setPasswordHash(passwordHashService.sha256(password));
        user.setAuthSource("LOCAL");
        user.setPasswordInitialized(true);
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

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse ssoLogin(SsoLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmployeeCode()) || !StringUtils.hasText(request.getPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "Employee code and password are required");
        }

        SsoEmployeeProfile profile = ssoAuthClient.loginAndFetchProfile(
            request.getEmployeeCode().trim(),
            request.getPassword()
        );
        String email = normalizeEmail(profile == null ? null : profile.getEmail());
        String employeeCode = normalizeEmployeeCode(profile.getEmployeeCode(), request.getEmployeeCode());
        String displayName = normalizeDisplayName(profile.getName(), email);

        UserEntity user = findByEmail(email);
        Date now = new Date();
        if (user == null) {
            user = new UserEntity();
            user.setUsername(generateAvailableUsername(email));
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setPasswordHash(passwordHashService.sha256(generateRandomPassword()));
            user.setRole("USER");
            user.setStatus("ENABLE");
            user.setAuthSource("SSO");
            user.setPasswordInitialized(false);
            user.setCreatedAt(now);
        } else if (!"ENABLE".equalsIgnoreCase(user.getStatus())) {
            throw new DomainException("AUTH_FORBIDDEN", "User is disabled");
        } else {
            user.setAuthSource(isPasswordInitialized(user) ? "LOCAL_SSO" : "SSO");
        }
        user.setEmployeeCode(employeeCode);
        user.setSsoUserId(profile.getSsoUserId());
        user.setUpdatedAt(now);

        try {
            if (user.getId() == null) {
                userMapper.insert(user);
            } else {
                userMapper.updateById(user);
            }
        } catch (DuplicateKeyException ex) {
            throw new DomainException("SSO_USER_CONFLICT", "SSO用户信息与现有账号冲突，请联系管理员");
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

    public AuthProfileResponse profile(String authorizationHeader) {
        AuthSession session = requireSession(authorizationHeader);
        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Current user does not exist");
        }
        return buildProfileResponse(user);
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
        if (!isPasswordInitialized(user)) {
            throw new DomainException("AUTH_PASSWORD_NOT_INITIALIZED", "当前账号尚未设置平台密码，请使用设置平台密码功能");
        }
        if (!passwordHashService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "Current password is incorrect");
        }
        if (passwordHashService.matches(newPassword, user.getPasswordHash())) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "New password cannot be the same as current password");
        }

        user.setPasswordHash(passwordHashService.sha256(newPassword));
        user.setPasswordInitialized(true);
        userMapper.updateById(user);
        authTokenService.removeToken(extractBearerToken(authorizationHeader));
    }

    public void setPassword(String authorizationHeader, SetPasswordRequest request) {
        AuthSession session = requireSession(authorizationHeader);
        if (request == null || !StringUtils.hasText(request.getNewPassword())) {
            throw new DomainException("AUTH_PARAM_INVALID", "New password is required");
        }
        String newPassword = request.getNewPassword().trim();
        if (newPassword.length() < 4) {
            throw new DomainException("AUTH_PASSWORD_INVALID", "New password must be at least 4 characters");
        }

        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Current user does not exist");
        }
        if (isPasswordInitialized(user)) {
            throw new DomainException("AUTH_PASSWORD_INITIALIZED", "当前账号已设置平台密码，请使用修改密码功能");
        }

        user.setPasswordHash(passwordHashService.sha256(newPassword));
        user.setPasswordInitialized(true);
        user.setUpdatedAt(new Date());
        userMapper.updateById(user);
    }

    public AuthProfileResponse updateGitlabUsername(String authorizationHeader, UpdateGitlabUsernameRequest request) {
        AuthSession session = requireSession(authorizationHeader);
        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Current user does not exist");
        }

        user.setGitlabUsername(normalizeGitlabUsername(request == null ? null : request.getGitlabUsername()));
        user.setUpdatedAt(new Date());
        userMapper.updateById(user);
        return buildProfileResponse(user);
    }

    private UserEntity findByUsername(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("username", username).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private UserEntity findByUsernameOrEmail(String identifier) {
        UserEntity user = findByUsername(identifier);
        if (user != null) {
            return user;
        }
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("email", identifier.trim().toLowerCase(Locale.ROOT)).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private UserEntity findByEmail(String email) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("email", email).last("limit 1");
        return userMapper.selectOne(wrapper);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new DomainException("AUTH_EMAIL_REQUIRED", "邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new DomainException("AUTH_EMAIL_INVALID", "邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeEmployeeCode(String ssoEmployeeCode, String fallbackEmployeeCode) {
        if (StringUtils.hasText(ssoEmployeeCode)) {
            return ssoEmployeeCode.trim();
        }
        return fallbackEmployeeCode == null ? null : fallbackEmployeeCode.trim();
    }

    private String normalizeDisplayName(String name, String email) {
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private String generateAvailableUsername(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        String baseUsername = generateUsernameFromEmailLocalPart(localPart);
        String candidate = truncateUsername(baseUsername);
        int suffix = 2;
        while (findByUsername(candidate) != null) {
            String suffixText = String.valueOf(suffix);
            candidate = truncateUsername(baseUsername, suffixText.length()) + suffixText;
            suffix++;
        }
        return candidate;
    }

    private String generateUsernameFromEmailLocalPart(String localPart) {
        String normalized = localPart == null ? "" : localPart.trim().toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("_");
        if (parts.length == 2 && parts[0].matches("[a-z]+") && parts[1].matches("[a-z]+[0-9]*")) {
            String second = parts[1];
            int digitStart = second.length();
            while (digitStart > 0 && Character.isDigit(second.charAt(digitStart - 1))) {
                digitStart--;
            }
            return sanitizeUsername(second.substring(0, digitStart) + parts[0] + second.substring(digitStart));
        }
        return sanitizeUsername(normalized);
    }

    private String sanitizeUsername(String value) {
        String sanitized = value.replaceAll("[^a-z0-9_]", "");
        if (StringUtils.hasText(sanitized)) {
            return sanitized;
        }
        return "user";
    }

    private String truncateUsername(String username) {
        return truncateUsername(username, 0);
    }

    private String truncateUsername(String username, int reservedSuffixLength) {
        int maxLength = Math.max(1, 64 - reservedSuffixLength);
        return username.length() <= maxLength ? username : username.substring(0, maxLength);
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
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

    private AuthProfileResponse buildProfileResponse(UserEntity user) {
        AuthProfileResponse response = new AuthProfileResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setEmail(user.getEmail());
        response.setEmployeeCode(user.getEmployeeCode());
        response.setSsoUserId(user.getSsoUserId());
        response.setAuthSource(user.getAuthSource());
        response.setPasswordInitialized(isPasswordInitialized(user));
        response.setGitlabUsername(user.getGitlabUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    private boolean isPasswordInitialized(UserEntity user) {
        return user.getPasswordInitialized() == null || Boolean.TRUE.equals(user.getPasswordInitialized());
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
