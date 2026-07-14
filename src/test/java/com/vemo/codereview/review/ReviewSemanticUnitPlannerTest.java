package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewFileContext;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import com.vemo.codereview.review.service.ChangeReviewFilter;
import com.vemo.codereview.review.service.ReviewSemanticUnitPlanner;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewSemanticUnitPlannerTest {

    @Test
    void shouldDeduplicateSemanticSnippetsAndKeepDeletedDiff() {
        ReviewExecutionContext context = new ReviewExecutionContext();
        GitLabChangesPayload changes = new GitLabChangesPayload();
        changes.setChanges(Arrays.asList(change("src/A.java", false), change("src/Deleted.java", true)));
        context.setMergeRequestChanges(changes);
        ReviewFileContext file = new ReviewFileContext();
        file.setFilePath("src/A.java");
        file.setChangeType("MODIFIED");
        file.setRef("head");
        file.setSnippets(Arrays.asList(snippet(10, 30), snippet(10, 30), snippet(40, 50)));
        context.setFileContexts(Arrays.asList(file));

        List<ReviewSemanticUnit> units = new ReviewSemanticUnitPlanner(new ChangeReviewFilter()).plan(context, true);

        assertEquals(3, units.size());
        assertEquals("src/A.java|METHOD|10|30", units.get(0).getUnitKey());
        assertEquals("src/A.java|METHOD|40|50", units.get(1).getUnitKey());
        assertEquals("DIFF_ONLY", units.get(2).getSemanticType());
    }

    @Test
    void shouldCreateOneFileDiffUnitWhenContextIsDisabled() {
        ReviewExecutionContext context = new ReviewExecutionContext();
        GitLabChangesPayload changes = new GitLabChangesPayload();
        changes.setChanges(Arrays.asList(change("src/A.java", false)));
        context.setMergeRequestChanges(changes);

        List<ReviewSemanticUnit> units = new ReviewSemanticUnitPlanner(new ChangeReviewFilter()).plan(context, false);

        assertEquals(1, units.size());
        assertEquals("FILE_DIFF", units.get(0).getSemanticType());
    }

    private GitLabChangesPayload.Change change(String path, boolean deleted) {
        GitLabChangesPayload.Change change = new GitLabChangesPayload.Change();
        change.setOldPath(path);
        change.setNewPath(path);
        change.setDeletedFile(deleted);
        change.setDiff("@@ -1 +1 @@\n+change");
        return change;
    }

    private ReviewCodeSnippet snippet(int start, int end) {
        ReviewCodeSnippet snippet = new ReviewCodeSnippet();
        snippet.setTitle("METHOD");
        snippet.setStartLine(start);
        snippet.setEndLine(end);
        snippet.setContent("method " + start);
        return snippet;
    }
}
