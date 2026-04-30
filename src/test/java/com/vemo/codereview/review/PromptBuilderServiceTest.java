package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.common.config.ReviewRuleProperties;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewExecutionContext;
import com.vemo.codereview.review.model.ReviewPromptPayload;
import com.vemo.codereview.review.service.ChangeReviewFilter;
import com.vemo.codereview.review.service.DiffChunkService;
import com.vemo.codereview.review.service.PromptBuilderService;
import com.vemo.codereview.review.service.ReviewRuleService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PromptBuilderServiceTest {

    @Test
    void shouldOnlyIncludeReviewableFilesInPrompt() {
        ReviewRuleProperties properties = new ReviewRuleProperties();
        ReviewRuleService reviewRuleService = new ReviewRuleService(properties);
        reviewRuleService.init();

        PromptBuilderService promptBuilderService =
            new PromptBuilderService(new ChangeReviewFilter(), new DiffChunkService(), reviewRuleService);

        GitLabChangesPayload.Change javaChange = new GitLabChangesPayload.Change();
        javaChange.setNewPath("src/main/java/com/example/ReviewService.java");
        javaChange.setOldPath("src/main/java/com/example/ReviewService.java");
        javaChange.setDiff("@@ -1,2 +1,5 @@\n+public class ReviewService {}");

        GitLabChangesPayload.Change generatedChange = new GitLabChangesPayload.Change();
        generatedChange.setNewPath("src/generated/ReviewService.java");
        generatedChange.setOldPath("src/generated/ReviewService.java");
        generatedChange.setDiff("@@ -1 +1 @@\n+generated");

        GitLabChangesPayload.Change lockFileChange = new GitLabChangesPayload.Change();
        lockFileChange.setNewPath("package-lock.json");
        lockFileChange.setOldPath("package-lock.json");
        lockFileChange.setDiff("{ }");

        GitLabChangesPayload response = new GitLabChangesPayload();
        response.setId(501L);
        response.setIid(7L);
        response.setTitle("Add review pipeline");
        response.setChanges(Arrays.asList(javaChange, generatedChange, lockFileChange));

        ReviewExecutionContext context = new ReviewExecutionContext();
        context.setTaskId(1L);
        context.setProjectId(1001L);
        context.setTargetId("7");
        context.setTargetTitle("Add review pipeline");
        context.setTargetType("merge_request");
        context.setMergeRequestChanges(response);

        ReviewPromptPayload prompt = promptBuilderService.build(context);

        assertEquals(1, prompt.getFiles().size());
        assertEquals("src/main/java/com/example/ReviewService.java", prompt.getFiles().get(0).getFilePath());
        assertFalse(prompt.getFiles().get(0).getDiffChunks().isEmpty());
        assertTrue(prompt.getUserPrompt().contains("Team review rules"));
        assertTrue(prompt.getUserPrompt().contains("Merge Request IID: 7"));
        assertTrue(prompt.getUserPrompt().contains("Reviewable files: 1"));
        assertTrue(prompt.getUserPrompt().contains("overall code quality before programmatic hard-rule deductions"));
        assertTrue(prompt.getSystemPrompt().contains("strict JSON"));
    }

    @Test
    void shouldRenderCommitMetadataForPushReview() {
        ReviewRuleProperties properties = new ReviewRuleProperties();
        ReviewRuleService reviewRuleService = new ReviewRuleService(properties);
        reviewRuleService.init();

        PromptBuilderService promptBuilderService =
            new PromptBuilderService(new ChangeReviewFilter(), new DiffChunkService(), reviewRuleService);

        GitLabChangesPayload.Change javaChange = new GitLabChangesPayload.Change();
        javaChange.setNewPath("src/main/java/com/example/PushReviewService.java");
        javaChange.setOldPath("src/main/java/com/example/PushReviewService.java");
        javaChange.setDiff("@@ -1 +1 @@\n+public class PushReviewService {}");

        GitLabChangesPayload response = new GitLabChangesPayload();
        response.setTitle("fix: push review test");
        response.setChanges(Arrays.asList(javaChange));

        ReviewExecutionContext context = new ReviewExecutionContext();
        context.setTaskId(2L);
        context.setProjectId(1001L);
        context.setTargetId("abcdef1234567890");
        context.setTargetTitle("fix: push review test");
        context.setTargetType("commit");
        context.setMergeRequestChanges(response);

        ReviewPromptPayload prompt = promptBuilderService.build(context);

        assertTrue(prompt.getUserPrompt().contains("Commit SHA: abcdef1234567890"));
        assertTrue(prompt.getUserPrompt().contains("Commit Title: fix: push review test"));
        assertTrue(prompt.getUserPrompt().contains("Total changes: 1"));
        assertTrue(prompt.getUserPrompt().contains("Reviewable files: 1"));
        assertTrue(prompt.getUserPrompt().contains("base score from 0 to 100"));
    }
}