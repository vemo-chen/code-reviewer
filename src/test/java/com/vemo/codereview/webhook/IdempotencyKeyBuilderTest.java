package com.vemo.codereview.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.vemo.codereview.webhook.model.GitLabWebhookPayload;
import com.vemo.codereview.webhook.support.IdempotencyKeyBuilder;
import org.junit.jupiter.api.Test;

class IdempotencyKeyBuilderTest {

    private final IdempotencyKeyBuilder builder = new IdempotencyKeyBuilder();

    @Test
    void shouldBuildPushKeyFromProjectBranchAndRange() {
        assertEquals(builder.buildForPush(push("test-cr", "b1", "a1")),
            builder.buildForPush(push("test-cr", "b1", "a1")));
        assertNotEquals(builder.buildForPush(push("test-cr", "b1", "a1")),
            builder.buildForPush(push("source-cr", "b1", "a1")));
        assertNotEquals(builder.buildForPush(push("test-cr", "b1", "a1")),
            builder.buildForPush(push("test-cr", "b2", "a1")));
    }

    private GitLabWebhookPayload push(String branch, String before, String after) {
        GitLabWebhookPayload payload = new GitLabWebhookPayload();
        GitLabWebhookPayload.Project project = new GitLabWebhookPayload.Project();
        project.setId(1001L);
        payload.setProject(project);
        payload.setRef("refs/heads/" + branch);
        payload.setBefore(before);
        payload.setAfter(after);
        return payload;
    }
}
