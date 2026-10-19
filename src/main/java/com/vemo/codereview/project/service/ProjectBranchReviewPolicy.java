package com.vemo.codereview.project.service;

import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.dashboard.entity.ProjectProfileEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectBranchReviewPolicy {

    public boolean isAllowed(ProjectProfileEntity project, String branch) {
        if (project == null || !StringUtils.hasText(branch)) {
            return false;
        }
        String normalizedBranch = branch.trim();
        Set<String> excludedBranches = parseBranches(project.getExcludedReviewBranches());
        if (excludedBranches.contains(normalizedBranch)) {
            return false;
        }
        Set<String> includedBranches = parseBranches(project.getReviewBranches());
        return includedBranches.isEmpty() || includedBranches.contains(normalizedBranch);
    }

    public List<String> resolveBranches(ProjectProfileEntity project, List<String> requestedBranches,
                                        List<String> availableBranches) {
        LinkedHashSet<String> requested = normalizeBranches(requestedBranches);
        if (!requested.isEmpty()) {
            List<String> result = new ArrayList<String>();
            for (String branch : requested) {
                if (!isAllowed(project, branch)) {
                    throw new DomainException("CUSTOM_REVIEW_BRANCH_SCOPE_INVALID",
                        "Custom review branch scope is invalid");
                }
                result.add(branch);
            }
            return result;
        }

        LinkedHashSet<String> candidates = parseBranches(project == null ? null : project.getReviewBranches());
        if (candidates.isEmpty()) {
            candidates = normalizeBranches(availableBranches);
        }
        List<String> result = new ArrayList<String>();
        for (String branch : candidates) {
            if (isAllowed(project, branch)) {
                result.add(branch);
            }
        }
        return result;
    }

    public LinkedHashSet<String> parseBranches(String rawBranches) {
        LinkedHashSet<String> branches = new LinkedHashSet<String>();
        if (!StringUtils.hasText(rawBranches)) {
            return branches;
        }
        String[] tokens = rawBranches.split("[,;\\s]+");
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                branches.add(token.trim());
            }
        }
        return branches;
    }

    private LinkedHashSet<String> normalizeBranches(List<String> rawBranches) {
        LinkedHashSet<String> branches = new LinkedHashSet<String>();
        if (rawBranches == null) {
            return branches;
        }
        for (String branch : rawBranches) {
            if (StringUtils.hasText(branch)) {
                branches.add(branch.trim());
            }
        }
        return branches;
    }
}
