package com.vemo.codereview.platform.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.vemo.codereview.CodeReviewerApplication;
import com.vemo.codereview.platform.gitlab.service.GitLabCommentPublisher;
import com.vemo.codereview.platform.gitlab.service.GitLabReviewTargetService;
import com.vemo.codereview.review.entity.CodeReviewCommentEntity;
import com.vemo.codereview.review.entity.CodeReviewResultEntity;
import com.vemo.codereview.review.mapper.ReviewCommentStoreMapper;
import com.vemo.codereview.review.mapper.ReviewResultStoreMapper;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(classes = CodeReviewerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:gitlab-publisher-db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Sql(scripts = "/db/schema.sql")
class GitLabCommentPublisherTest {

    @Autowired
    private GitLabCommentPublisher gitLabCommentPublisher;

    @Autowired
    private ReviewResultStoreMapper codeReviewResultMapper;

    @Autowired
    private ReviewCommentStoreMapper codeReviewCommentMapper;

    @MockBean
    private GitLabReviewTargetService gitLabReviewTargetService;

    @Test
    void shouldPublishResultAsMergeRequestNoteAndMarkCommentsPosted() {
        Date now = new Date();

        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setTaskId(101L);
        result.setProviderName("openai-compatible");
        result.setModelName("deepseek-chat");
        result.setRiskLevel("HIGH");
        result.setSummary("Found one issue");
        result.setAdvice("Fix before merge");
        result.setCreatedAt(now);
        codeReviewResultMapper.insert(result);

        CodeReviewCommentEntity comment = new CodeReviewCommentEntity();
        comment.setResultId(result.getId());
        comment.setFilePath("src/main/java/com/example/A.java");
        comment.setLineNo(12);
        comment.setSeverity("HIGH");
        comment.setCategory("STYLE");
        comment.setMessage("Rename variable");
        comment.setSuggestion("Use a meaningful name");
        comment.setCommentHash("hash-1");
        comment.setIsPosted(Boolean.FALSE);
        comment.setCreatedAt(now);
        codeReviewCommentMapper.insert(comment);

        boolean firstPublish = gitLabCommentPublisher.publishMergeRequest(1001L, "7", result);
        CodeReviewCommentEntity savedComment = codeReviewCommentMapper.selectById(comment.getId());

        assertTrue(firstPublish);
        assertNotNull(savedComment.getPostedAt());
        assertEquals(Boolean.TRUE, savedComment.getIsPosted());
        verify(gitLabReviewTargetService, times(1))
            .publishMergeRequestNote(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("7"),
                org.mockito.ArgumentMatchers.contains("Found one issue"),
                org.mockito.ArgumentMatchers.isNull());

        boolean secondPublish = gitLabCommentPublisher.publishMergeRequest(1001L, "7", result);
        assertFalse(secondPublish);
        verify(gitLabReviewTargetService, times(1))
            .publishMergeRequestNote(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("7"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void shouldPublishResultAsCommitNote() {
        Date now = new Date();

        CodeReviewResultEntity result = new CodeReviewResultEntity();
        result.setTaskId(102L);
        result.setProviderName("openai-compatible");
        result.setModelName("deepseek-chat");
        result.setRiskLevel("MEDIUM");
        result.setSummary("Push review summary");
        result.setAdvice("Fix before release");
        result.setCreatedAt(now);
        codeReviewResultMapper.insert(result);

        CodeReviewCommentEntity comment = new CodeReviewCommentEntity();
        comment.setResultId(result.getId());
        comment.setFilePath("src/main/java/com/example/B.java");
        comment.setLineNo(18);
        comment.setSeverity("MEDIUM");
        comment.setCategory("STYLE");
        comment.setMessage("Rename field");
        comment.setSuggestion("Use a domain name");
        comment.setCommentHash("hash-2");
        comment.setIsPosted(Boolean.FALSE);
        comment.setCreatedAt(now);
        codeReviewCommentMapper.insert(comment);

        boolean published = gitLabCommentPublisher.publishCommit(1001L, "abcdef123456", result);

        assertTrue(published);
        verify(gitLabReviewTargetService, times(1))
            .publishCommitNote(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("abcdef123456"),
                org.mockito.ArgumentMatchers.contains("Push review summary"),
                org.mockito.ArgumentMatchers.isNull());
    }
}
