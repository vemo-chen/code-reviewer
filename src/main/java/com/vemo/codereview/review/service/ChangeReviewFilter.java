package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChangeReviewFilter {

    private static final String[] SKIP_PATH_KEYWORDS = {
        "/generated/",
        "/gen/",
        "/target/",
        "/build/",
        "/dist/",
        "/node_modules/"
    };

    private static final String[] SKIP_FILE_SUFFIXES = {
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".ico",
        ".jar", ".class", ".zip", ".gz", ".tar",
        ".min.js", ".min.css"
    };

    private static final String[] SKIP_FILE_NAMES = {
        "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "Cargo.lock"
    };

    public List<GitLabChangesPayload.Change> filterReviewableChanges(
        List<GitLabChangesPayload.Change> changes,
        String supportedFileExtensions) {
        if (changes == null || changes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> allowedExtensions = parseSupportedExtensions(supportedFileExtensions);
        List<GitLabChangesPayload.Change> result = new ArrayList<GitLabChangesPayload.Change>();
        for (GitLabChangesPayload.Change change : changes) {
            if (change == null) {
                continue;
            }
            String path = StringUtils.hasText(change.getNewPath()) ? change.getNewPath() : change.getOldPath();
            if (!StringUtils.hasText(path)
                || shouldSkipByPath(path)
                || !StringUtils.hasText(change.getDiff())
                || !matchesConfiguredExtensions(path, allowedExtensions)) {
                continue;
            }
            result.add(change);
        }
        return result;
    }

    private boolean shouldSkipByPath(String path) {
        String normalizedPath = path.replace('\\', '/').toLowerCase();
        for (String keyword : SKIP_PATH_KEYWORDS) {
            if (normalizedPath.contains(keyword)) {
                return true;
            }
        }
        for (String fileName : SKIP_FILE_NAMES) {
            if (normalizedPath.endsWith("/" + fileName.toLowerCase()) || normalizedPath.equals(fileName.toLowerCase())) {
                return true;
            }
        }
        for (String suffix : SKIP_FILE_SUFFIXES) {
            if (normalizedPath.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesConfiguredExtensions(String path, Set<String> allowedExtensions) {
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        String normalizedPath = path.replace('\\', '/').toLowerCase();
        for (String extension : allowedExtensions) {
            if (normalizedPath.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseSupportedExtensions(String supportedFileExtensions) {
        if (!StringUtils.hasText(supportedFileExtensions)) {
            return Collections.emptySet();
        }
        String[] items = supportedFileExtensions.split("[,;\\s]+");
        Set<String> result = new LinkedHashSet<String>();
        for (String item : items) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String normalized = item.trim().toLowerCase();
            if (!normalized.startsWith(".")) {
                normalized = "." + normalized;
            }
            result.add(normalized);
        }
        return result;
    }
}
