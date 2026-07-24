package com.vemo.codereview.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.auth.service.PasswordHashService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.user.entity.UserEntity;
import com.vemo.codereview.user.entity.UserProjectRelEntity;
import com.vemo.codereview.user.mapper.UserMapper;
import com.vemo.codereview.user.mapper.UserProjectRelMapper;
import com.vemo.codereview.user.model.ProjectUsersResponse;
import com.vemo.codereview.user.model.ResetPasswordRequest;
import com.vemo.codereview.user.model.UserDetailResponse;
import com.vemo.codereview.user.model.UserPageResponse;
import com.vemo.codereview.user.model.UserProjectAssignRequest;
import com.vemo.codereview.user.model.UserProjectAssignResponse;
import com.vemo.codereview.user.model.UserQueryRequest;
import com.vemo.codereview.user.model.UserStatusUpdateRequest;
import com.vemo.codereview.user.model.UserUpsertRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserProjectRelMapper userProjectRelMapper;
    private final ProjectProfileMapper projectProfileMapper;
    private final PasswordHashService passwordHashService;
    private final CurrentUserService currentUserService;

    public UserService(
        UserMapper userMapper,
        UserProjectRelMapper userProjectRelMapper,
        ProjectProfileMapper projectProfileMapper,
        PasswordHashService passwordHashService,
        CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.userProjectRelMapper = userProjectRelMapper;
        this.projectProfileMapper = projectProfileMapper;
        this.passwordHashService = passwordHashService;
        this.currentUserService = currentUserService;
    }

    public UserPageResponse page(UserQueryRequest request) {
        long pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        long pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        if (StringUtils.hasText(request.getUsername())) {
            wrapper.like("username", request.getUsername().trim());
        }
        if (StringUtils.hasText(request.getDisplayName())) {
            wrapper.like("display_name", request.getDisplayName().trim());
        }
        if (StringUtils.hasText(request.getEmail())) {
            wrapper.like("email", request.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(request.getGitlabUsername())) {
            wrapper.like("gitlab_username", request.getGitlabUsername().trim());
        }
        if (StringUtils.hasText(request.getRole())) {
            wrapper.eq("role", normalizeRole(request.getRole()));
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq("status", normalizeStatus(request.getStatus()));
        }
        wrapper.ne("username", "admin");
        wrapper.orderByDesc("id");

        Page<UserEntity> page = userMapper.selectPage(new Page<UserEntity>(pageNo, pageSize), wrapper);
        Map<Long, Integer> projectCountMap = buildProjectCountMap(page.getRecords());

        List<UserPageResponse.Item> records = new ArrayList<UserPageResponse.Item>();
        for (UserEntity entity : page.getRecords()) {
            UserPageResponse.Item item = new UserPageResponse.Item();
            item.setId(entity.getId());
            item.setUsername(entity.getUsername());
            item.setDisplayName(entity.getDisplayName());
            item.setEmail(entity.getEmail());
            item.setEmployeeCode(entity.getEmployeeCode());
            item.setAuthSource(entity.getAuthSource());
            item.setPasswordInitialized(isPasswordInitialized(entity));
            item.setGitlabUsername(entity.getGitlabUsername());
            item.setRole(entity.getRole());
            item.setStatus(entity.getStatus());
            item.setProjectCount(projectCountMap.containsKey(entity.getId()) ? projectCountMap.get(entity.getId()) : 0);
            item.setCreatedAt(entity.getCreatedAt());
            item.setUpdatedAt(entity.getUpdatedAt());
            records.add(item);
        }

        UserPageResponse response = new UserPageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotal());
        response.setRecords(records);
        return response;
    }

    public UserDetailResponse getById(Long userId) {
        requireAdmin();
        return toDetailResponse(requireUser(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserDetailResponse create(UserUpsertRequest request) {
        requireAdmin();
        validateCreateRequest(request);
        if (existsByUsername(request.getUsername().trim(), null)) {
            throw new DomainException("USER_USERNAME_DUPLICATE", "Username already exists");
        }
        String email = normalizeEmail(request.getEmail());
        if (existsByEmail(email, null)) {
            throw new DomainException("USER_EMAIL_DUPLICATE", "邮箱已存在");
        }

        Date now = new Date();
        UserEntity entity = new UserEntity();
        entity.setUsername(request.getUsername().trim());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setEmail(email);
        entity.setGitlabUsername(normalizeGitlabUsername(request.getGitlabUsername()));
        entity.setPasswordHash(passwordHashService.sha256(request.getPassword().trim()));
        entity.setAuthSource("LOCAL");
        entity.setPasswordInitialized(true);
        entity.setRole(normalizeRole(request.getRole()));
        entity.setStatus(normalizeStatus(request.getStatus()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserDetailResponse update(Long userId, UserUpsertRequest request) {
        requireAdmin();
        validateUpdateRequest(request);
        UserEntity entity = requireUser(userId);
        assertNotBuiltInAdmin(entity);
        if (existsByUsername(request.getUsername().trim(), userId)) {
            throw new DomainException("USER_USERNAME_DUPLICATE", "Username already exists");
        }
        String email = normalizeEmail(request.getEmail());
        if (existsByEmail(email, userId)) {
            throw new DomainException("USER_EMAIL_DUPLICATE", "邮箱已存在");
        }

        entity.setUsername(request.getUsername().trim());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setEmail(email);
        entity.setGitlabUsername(normalizeGitlabUsername(request.getGitlabUsername()));
        entity.setRole(normalizeRole(request.getRole()));
        entity.setStatus(normalizeStatus(request.getStatus()));
        entity.setUpdatedAt(new Date());
        userMapper.updateById(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserDetailResponse updateStatus(Long userId, UserStatusUpdateRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new DomainException("USER_STATUS_REQUIRED", "User status is required");
        }
        UserEntity entity = requireUser(userId);
        assertNotBuiltInAdmin(entity);
        entity.setStatus(normalizeStatus(request.getStatus()));
        entity.setUpdatedAt(new Date());
        userMapper.updateById(entity);
        return toDetailResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.getPassword())) {
            throw new DomainException("USER_PASSWORD_REQUIRED", "Password is required");
        }
        UserEntity entity = requireUser(userId);
        assertNotBuiltInAdmin(entity);
        entity.setPasswordHash(passwordHashService.sha256(request.getPassword().trim()));
        entity.setPasswordInitialized(true);
        entity.setUpdatedAt(new Date());
        userMapper.updateById(entity);
    }

    public UserProjectAssignResponse getUserProjects(Long userId) {
        requireAdmin();
        requireUser(userId);

        QueryWrapper<UserProjectRelEntity> wrapper = new QueryWrapper<UserProjectRelEntity>();
        wrapper.eq("user_id", userId).orderByAsc("id");
        List<UserProjectRelEntity> relations = userProjectRelMapper.selectList(wrapper);

        List<Long> projectIds = new ArrayList<Long>();
        for (UserProjectRelEntity relation : relations) {
            if (relation.getProjectId() != null) {
                projectIds.add(relation.getProjectId());
            }
        }

        UserProjectAssignResponse response = new UserProjectAssignResponse();
        response.setUserId(userId);
        response.setProjectIds(projectIds);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProjectAssignResponse assignUserProjects(Long userId, UserProjectAssignRequest request) {
        requireAdmin();
        UserEntity entity = requireUser(userId);
        assertNotBuiltInAdmin(entity);

        List<Long> projectIds = request == null ? Collections.<Long>emptyList() : request.getProjectIds();
        validateProjectIds(projectIds);

        QueryWrapper<UserProjectRelEntity> deleteWrapper = new QueryWrapper<UserProjectRelEntity>();
        deleteWrapper.eq("user_id", userId);
        userProjectRelMapper.delete(deleteWrapper);

        if (!CollectionUtils.isEmpty(projectIds)) {
            Date now = new Date();
            Set<Long> uniqueProjectIds = new HashSet<Long>(projectIds);
            for (Long projectId : uniqueProjectIds) {
                UserProjectRelEntity relation = new UserProjectRelEntity();
                relation.setUserId(userId);
                relation.setProjectId(projectId);
                relation.setCreatedAt(now);
                userProjectRelMapper.insert(relation);
            }
        }

        return getUserProjects(userId);
    }

    public ProjectUsersResponse getProjectUsers(Long projectId) {
        if (projectProfileMapper.selectById(projectId) == null) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project not found");
        }

        QueryWrapper<UserProjectRelEntity> relWrapper = new QueryWrapper<UserProjectRelEntity>();
        relWrapper.eq("project_id", projectId).orderByAsc("id");
        List<UserProjectRelEntity> relations = userProjectRelMapper.selectList(relWrapper);
        if (relations.isEmpty()) {
            ProjectUsersResponse response = new ProjectUsersResponse();
            response.setProjectId(projectId);
            response.setUsers(new ArrayList<ProjectUsersResponse.Item>());
            return response;
        }

        Set<Long> userIds = new HashSet<Long>();
        for (UserProjectRelEntity relation : relations) {
            if (relation.getUserId() != null) {
                userIds.add(relation.getUserId());
            }
        }

        QueryWrapper<UserEntity> userWrapper = new QueryWrapper<UserEntity>();
        userWrapper.in("id", userIds).orderByAsc("id");
        List<UserEntity> users = userMapper.selectList(userWrapper);

        List<ProjectUsersResponse.Item> items = new ArrayList<ProjectUsersResponse.Item>();
        for (UserEntity user : users) {
            ProjectUsersResponse.Item item = new ProjectUsersResponse.Item();
            item.setId(user.getId());
            item.setUsername(user.getUsername());
            item.setDisplayName(user.getDisplayName());
            item.setRole(user.getRole());
            item.setStatus(user.getStatus());
            items.add(item);
        }

        ProjectUsersResponse response = new ProjectUsersResponse();
        response.setProjectId(projectId);
        response.setUsers(items);
        return response;
    }

    private void validateCreateRequest(UserUpsertRequest request) {
        if (request == null) {
            throw new DomainException("USER_PARAM_INVALID", "User request is required");
        }
        if (!StringUtils.hasText(request.getUsername())) {
            throw new DomainException("USER_USERNAME_REQUIRED", "Username is required");
        }
        if (!StringUtils.hasText(request.getDisplayName())) {
            throw new DomainException("USER_DISPLAY_NAME_REQUIRED", "Display name is required");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new DomainException("USER_EMAIL_REQUIRED", "邮箱不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new DomainException("USER_PASSWORD_REQUIRED", "Password is required");
        }
        normalizeRole(request.getRole());
        normalizeStatus(request.getStatus());
    }

    private void validateUpdateRequest(UserUpsertRequest request) {
        if (request == null) {
            throw new DomainException("USER_PARAM_INVALID", "User request is required");
        }
        if (!StringUtils.hasText(request.getUsername())) {
            throw new DomainException("USER_USERNAME_REQUIRED", "Username is required");
        }
        if (!StringUtils.hasText(request.getDisplayName())) {
            throw new DomainException("USER_DISPLAY_NAME_REQUIRED", "Display name is required");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new DomainException("USER_EMAIL_REQUIRED", "邮箱不能为空");
        }
        normalizeRole(request.getRole());
        normalizeStatus(request.getStatus());
    }

    private void validateProjectIds(List<Long> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return;
        }
        Set<Long> uniqueProjectIds = new HashSet<Long>(projectIds);
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.in("id", uniqueProjectIds).select("id");
        List<ProjectProfileEntity> projects = projectProfileMapper.selectList(wrapper);
        if (projects.size() != uniqueProjectIds.size()) {
            throw new DomainException("PROJECT_NOT_FOUND", "Project contains invalid id");
        }
    }

    private Map<Long, Integer> buildProjectCountMap(List<UserEntity> users) {
        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyMap();
        }
        Set<Long> userIds = new HashSet<Long>();
        for (UserEntity user : users) {
            if (user.getId() != null) {
                userIds.add(user.getId());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<UserProjectRelEntity> wrapper = new QueryWrapper<UserProjectRelEntity>();
        wrapper.in("user_id", userIds);
        List<UserProjectRelEntity> relations = userProjectRelMapper.selectList(wrapper);
        Map<Long, Integer> countMap = new HashMap<Long, Integer>();
        for (UserProjectRelEntity relation : relations) {
            Integer count = countMap.get(relation.getUserId());
            countMap.put(relation.getUserId(), count == null ? 1 : count + 1);
        }
        return countMap;
    }

    private boolean existsByUsername(String username, Long excludeUserId) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("username", username);
        if (excludeUserId != null) {
            wrapper.ne("id", excludeUserId);
        }
        wrapper.last("limit 1");
        return userMapper.selectOne(wrapper) != null;
    }

    private boolean existsByEmail(String email, Long excludeUserId) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<UserEntity>();
        wrapper.eq("email", email);
        if (excludeUserId != null) {
            wrapper.ne("id", excludeUserId);
        }
        wrapper.last("limit 1");
        return userMapper.selectOne(wrapper) != null;
    }

    private UserEntity requireUser(Long userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new DomainException("USER_NOT_FOUND", "User not found");
        }
        return entity;
    }

    private void assertNotBuiltInAdmin(UserEntity entity) {
        if (entity != null && "admin".equalsIgnoreCase(entity.getUsername())) {
            throw new DomainException("USER_BUILTIN_ADMIN_PROTECTED", "Built-in admin cannot be modified here");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new DomainException("USER_ROLE_REQUIRED", "User role is required");
        }
        String normalized = role.trim().toUpperCase();
        if (!"ADMIN".equals(normalized) && !"USER".equals(normalized)) {
            throw new DomainException("USER_ROLE_INVALID", "User role is invalid");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new DomainException("USER_STATUS_REQUIRED", "User status is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!"ENABLE".equals(normalized) && !"DISABLE".equals(normalized)) {
            throw new DomainException("USER_STATUS_INVALID", "User status is invalid");
        }
        return normalized;
    }

    private void requireAdmin() {
        if (!currentUserService.isAdmin()) {
            throw new DomainException("AUTH_FORBIDDEN", "Only admin can manage users");
        }
    }

    private UserDetailResponse toDetailResponse(UserEntity entity) {
        UserDetailResponse response = new UserDetailResponse();
        response.setId(entity.getId());
        response.setUsername(entity.getUsername());
        response.setDisplayName(entity.getDisplayName());
        response.setEmail(entity.getEmail());
        response.setEmployeeCode(entity.getEmployeeCode());
        response.setSsoUserId(entity.getSsoUserId());
        response.setAuthSource(entity.getAuthSource());
        response.setPasswordInitialized(isPasswordInitialized(entity));
        response.setGitlabUsername(entity.getGitlabUsername());
        response.setRole(entity.getRole());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new DomainException("USER_EMAIL_REQUIRED", "邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new DomainException("USER_EMAIL_INVALID", "邮箱格式不正确");
        }
        return normalized;
    }

    private boolean isPasswordInitialized(UserEntity entity) {
        return entity.getPasswordInitialized() == null || Boolean.TRUE.equals(entity.getPasswordInitialized());
    }

    private String normalizeGitlabUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            return null;
        }
        return gitlabUsername.trim();
    }
}
