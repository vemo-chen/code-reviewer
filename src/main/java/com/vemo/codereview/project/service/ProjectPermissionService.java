package com.vemo.codereview.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.auth.service.CurrentUserService;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import com.vemo.codereview.review.entity.CodeReviewTaskEntity;
import com.vemo.codereview.review.mapper.ReviewTaskStoreMapper;
import com.vemo.codereview.user.entity.UserProjectRelEntity;
import com.vemo.codereview.user.mapper.UserProjectRelMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProjectPermissionService {

    private final CurrentUserService currentUserService;
    private final UserProjectRelMapper userProjectRelMapper;
    private final ProjectProfileMapper projectProfileMapper;
    private final ReviewTaskStoreMapper reviewTaskStoreMapper;

    public ProjectPermissionService(
        CurrentUserService currentUserService,
        UserProjectRelMapper userProjectRelMapper,
        ProjectProfileMapper projectProfileMapper,
        ReviewTaskStoreMapper reviewTaskStoreMapper) {
        this.currentUserService = currentUserService;
        this.userProjectRelMapper = userProjectRelMapper;
        this.projectProfileMapper = projectProfileMapper;
        this.reviewTaskStoreMapper = reviewTaskStoreMapper;
    }

    public boolean isAdmin() {
        return currentUserService.isAdmin();
    }

    public List<Long> getAccessibleProjectIds() {
        if (currentUserService.isAdmin()) {
            return loadAllProjectIds();
        }

        Long userId = currentUserService.requireCurrentUserId();
        QueryWrapper<UserProjectRelEntity> wrapper = new QueryWrapper<UserProjectRelEntity>();
        wrapper.eq("user_id", userId).select("project_id").orderByAsc("id");
        List<UserProjectRelEntity> relations = userProjectRelMapper.selectList(wrapper);
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> projectIds = new LinkedHashSet<Long>();
        for (UserProjectRelEntity relation : relations) {
            if (relation.getProjectId() != null) {
                projectIds.add(relation.getProjectId());
            }
        }
        return new ArrayList<Long>(projectIds);
    }

    public List<Long> getOwnedProjectIds() {
        if (currentUserService.isAdmin()) {
            return loadAllProjectIds();
        }
        Long userId = currentUserService.requireCurrentUserId();
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.eq("owner_user_id", userId).select("id").orderByAsc("id");
        List<ProjectProfileEntity> projects = projectProfileMapper.selectList(wrapper);
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> projectIds = new ArrayList<Long>(projects.size());
        for (ProjectProfileEntity project : projects) {
            if (project.getId() != null) {
                projectIds.add(project.getId());
            }
        }
        return projectIds;
    }

    public boolean hasProjectAccess(Long projectId) {
        if (projectId == null) {
            return false;
        }
        if (currentUserService.isAdmin()) {
            return projectProfileMapper.selectById(projectId) != null;
        }
        return getAccessibleProjectIds().contains(projectId);
    }

    public void requireProjectAccess(Long projectId) {
        if (!hasProjectAccess(projectId)) {
            throw new DomainException("PROJECT_ACCESS_DENIED", "Current user cannot access this project");
        }
    }

    public boolean isProjectMember(Long projectId) {
        return hasProjectAccess(projectId);
    }

    public void requireProjectMember(Long projectId) {
        if (!isProjectMember(projectId)) {
            throw new DomainException("PROJECT_MEMBER_REQUIRED", "Current user is not a member of this project");
        }
    }

    public boolean isProjectOwner(Long projectId) {
        if (projectId == null) {
            return false;
        }
        if (currentUserService.isAdmin()) {
            return true;
        }
        ProjectProfileEntity project = projectProfileMapper.selectById(projectId);
        if (project == null || project.getOwnerUserId() == null) {
            return false;
        }
        return project.getOwnerUserId().equals(currentUserService.requireCurrentUserId());
    }

    public void requireProjectOwner(Long projectId) {
        if (!isProjectOwner(projectId)) {
            throw new DomainException("PROJECT_OWNER_REQUIRED", "Current user is not the owner of this project");
        }
    }

    public boolean hasTaskAccess(Long taskId) {
        if (taskId == null) {
            return false;
        }
        CodeReviewTaskEntity task = reviewTaskStoreMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        return hasProjectAccess(task.getProjectId());
    }

    public void requireTaskAccess(Long taskId) {
        if (!hasTaskAccess(taskId)) {
            throw new DomainException("TASK_ACCESS_DENIED", "Current user cannot access this task");
        }
    }

    private List<Long> loadAllProjectIds() {
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.select("id").orderByAsc("id");
        List<ProjectProfileEntity> projects = projectProfileMapper.selectList(wrapper);
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> projectIds = new ArrayList<Long>(projects.size());
        for (ProjectProfileEntity project : projects) {
            if (project.getId() != null) {
                projectIds.add(project.getId());
            }
        }
        return projectIds;
    }
}
