package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFileContext;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class ReviewSemanticUnitPlanner {
    private final ChangeReviewFilter changeReviewFilter;

    public ReviewSemanticUnitPlanner(ChangeReviewFilter changeReviewFilter) {
        this.changeReviewFilter = changeReviewFilter;
    }

    public List<ReviewSemanticUnit> plan(ReviewExecutionContext context, boolean contextEnabled) {
        List<GitLabChangesPayload.Change> changes = context.getMergeRequestChanges() == null
            ? Collections.<GitLabChangesPayload.Change>emptyList() : context.getMergeRequestChanges().getChanges();
        changes = changeReviewFilter.filterReviewableChanges(changes, context.getSupportedFileExtensions());
        Map<String, ReviewFileContext> files = byPath(context.getFileContexts());
        LinkedHashMap<String, ReviewSemanticUnit> units = new LinkedHashMap<String, ReviewSemanticUnit>();
        for (GitLabChangesPayload.Change change : changes) {
            String path = path(change);
            ReviewFileContext file = files.get(path);
            if (!contextEnabled || Boolean.TRUE.equals(change.getDeletedFile()) || file == null
                || CollectionUtils.isEmpty(file.getSnippets())) {
                String type = contextEnabled ? "DIFF_ONLY" : "FILE_DIFF";
                ReviewSemanticUnit unit = base(change, file, type, null, null);
                units.put(path + "|" + type, unit);
                continue;
            }
            for (ReviewCodeSnippet snippet : file.getSnippets()) {
                String semanticType = snippet.getTitle() == null ? "SEMANTIC" : snippet.getTitle();
                ReviewSemanticUnit unit = base(change, file, semanticType, snippet.getStartLine(), snippet.getEndLine());
                unit.setExpandedCode(snippet.getContent());
                units.put(unit.getUnitKey(), unit);
            }
        }
        return new ArrayList<ReviewSemanticUnit>(units.values());
    }

    private ReviewSemanticUnit base(GitLabChangesPayload.Change change, ReviewFileContext file,
                                    String type, Integer start, Integer end) {
        ReviewSemanticUnit unit = new ReviewSemanticUnit();
        unit.setFilePath(path(change));
        unit.setChangeType(resolveChangeType(change));
        unit.setSemanticType(type);
        unit.setStartLine(start);
        unit.setEndLine(end);
        unit.setDiff(change.getDiff());
        unit.setRef(file == null ? null : file.getRef());
        unit.setRiskHints(file == null ? null : file.getRiskHints());
        unit.setUnitKey(path(change) + "|" + type + (start == null ? "" : "|" + start + "|" + end));
        return unit;
    }

    private Map<String, ReviewFileContext> byPath(List<ReviewFileContext> contexts) {
        Map<String, ReviewFileContext> result = new HashMap<String, ReviewFileContext>();
        if (contexts != null) for (ReviewFileContext context : contexts) result.put(context.getFilePath(), context);
        return result;
    }
    private String path(GitLabChangesPayload.Change change) { return change.getNewPath() != null ? change.getNewPath() : change.getOldPath(); }
    private String resolveChangeType(GitLabChangesPayload.Change change) {
        if (Boolean.TRUE.equals(change.getNewFile())) return "ADDED";
        if (Boolean.TRUE.equals(change.getDeletedFile())) return "DELETED";
        if (Boolean.TRUE.equals(change.getRenamedFile())) return "RENAMED";
        return "MODIFIED";
    }
}
