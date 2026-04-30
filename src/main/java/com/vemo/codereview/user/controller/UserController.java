package com.vemo.codereview.user.controller;

import com.vemo.codereview.common.model.ApiResponse;
import com.vemo.codereview.user.model.ProjectUsersResponse;
import com.vemo.codereview.user.model.ResetPasswordRequest;
import com.vemo.codereview.user.model.UserDetailResponse;
import com.vemo.codereview.user.model.UserPageResponse;
import com.vemo.codereview.user.model.UserProjectAssignRequest;
import com.vemo.codereview.user.model.UserProjectAssignResponse;
import com.vemo.codereview.user.model.UserQueryRequest;
import com.vemo.codereview.user.model.UserStatusUpdateRequest;
import com.vemo.codereview.user.model.UserUpsertRequest;
import com.vemo.codereview.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ApiResponse<UserPageResponse> pageUsers(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String displayName,
        @RequestParam(required = false) String gitlabUsername,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status) {
        UserQueryRequest request = new UserQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setUsername(username);
        request.setDisplayName(displayName);
        request.setGitlabUsername(gitlabUsername);
        request.setRole(role);
        request.setStatus(status);
        return ApiResponse.success(userService.page(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserDetailResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getById(userId));
    }

    @PostMapping("/users")
    public ApiResponse<UserDetailResponse> createUser(@RequestBody UserUpsertRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<UserDetailResponse> updateUser(
        @PathVariable Long userId,
        @RequestBody UserUpsertRequest request) {
        return ApiResponse.success(userService.update(userId, request));
    }

    @PutMapping("/users/{userId}/status")
    public ApiResponse<UserDetailResponse> updateUserStatus(
        @PathVariable Long userId,
        @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success(userService.updateStatus(userId, request));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<String> resetPassword(
        @PathVariable Long userId,
        @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(userId, request);
        return ApiResponse.success("ok");
    }

    @GetMapping("/users/{userId}/projects")
    public ApiResponse<UserProjectAssignResponse> getUserProjects(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUserProjects(userId));
    }

    @PutMapping("/users/{userId}/projects")
    public ApiResponse<UserProjectAssignResponse> assignUserProjects(
        @PathVariable Long userId,
        @RequestBody UserProjectAssignRequest request) {
        return ApiResponse.success(userService.assignUserProjects(userId, request));
    }

    @GetMapping("/projects/{projectId}/users")
    public ApiResponse<ProjectUsersResponse> getProjectUsers(@PathVariable Long projectId) {
        return ApiResponse.success(userService.getProjectUsers(projectId));
    }
}
