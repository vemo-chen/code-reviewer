package com.vemo.codereview.project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import com.vemo.codereview.dashboard.mapper.ProjectProfileMapper;
import org.springframework.stereotype.Service;

@Service
public class ProjectConfigService {

    private final ProjectProfileMapper projectProfileMapper;

    public ProjectConfigService(ProjectProfileMapper projectProfileMapper) {
        this.projectProfileMapper = projectProfileMapper;
    }

    public ProjectProfileEntity findByGitLabProjectId(Long gitlabProjectId) {
        if (gitlabProjectId == null) {
            return null;
        }
        QueryWrapper<ProjectProfileEntity> wrapper = new QueryWrapper<ProjectProfileEntity>();
        wrapper.eq("source_platform", "gitlab")
            .eq("gitlab_project_id", gitlabProjectId)
            .last("limit 1");
        return projectProfileMapper.selectOne(wrapper);
    }

    public ProjectProfileEntity findById(Long id) {
        if (id == null) {
            return null;
        }
        return projectProfileMapper.selectById(id);
    }
}
