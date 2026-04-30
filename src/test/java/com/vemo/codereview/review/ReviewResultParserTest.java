package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.vemo.codereview.llm.model.ChatCompletionResponse;
import com.vemo.codereview.review.model.ReviewSummary;
import com.vemo.codereview.review.service.ReviewResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ReviewResultParserTest {

    @Test
    void shouldParseReviewJsonIntoNormalizedSummary() {
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Message message = new ChatCompletionResponse.Message();
        message.setRole("assistant");
        message.setContent("{"
            + "\"suggestedScore\":88,"
            + "\"summary\":\"Found 2 issues\","
            + "\"briefSummary\":\"Found 2 issues requiring attention\","
            + "\"riskLevel\":\"high\","
            + "\"advice\":\"Fix before merge\","
            + "\"comments\":[{"
            + "\"filePath\":\"src/main/java/com/example/ReviewService.java\","
            + "\"line\":18,"
            + "\"severity\":\"critical\","
            + "\"category\":\"NULL_POINTER\","
            + "\"message\":\"Potential null dereference\","
            + "\"suggestion\":\"Add null check\""
            + "}]"
            + "}");
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        response.setChoices(Collections.singletonList(choice));

        ReviewResponseParser parser = new ReviewResponseParser(new ObjectMapper());
        ReviewSummary summary = parser.parse(response);

        assertEquals(Integer.valueOf(88), summary.getSuggestedScore());
        assertEquals("Found 2 issues", summary.getSummary());
        assertEquals("Found 2 issues requiring attention", summary.getBriefSummary());
        assertEquals("HIGH", summary.getRiskLevel());
        assertEquals("Fix before merge", summary.getAdvice());
        assertEquals(1, summary.getComments().size());
        assertEquals("CRITICAL", summary.getComments().get(0).getSeverity());
        assertEquals("src/main/java/com/example/ReviewService.java", summary.getComments().get(0).getFilePath());
        assertNotNull(summary.getComments().get(0).getCommentHash());
    }

    @Test
    void shouldParseJsonWrappedInMarkdownCodeFence() {
        ChatCompletionResponse response = new ChatCompletionResponse();
        ChatCompletionResponse.Message message = new ChatCompletionResponse.Message();
        message.setRole("assistant");
        message.setContent("```json\n"
            + "{\n"
            + "  \"suggestedScore\": 91,\n"
            + "  \"summary\": \"MonitorConfig has a hard-rule issue\",\n"
            + "  \"briefSummary\": \"MonitorConfig needs changes before merge\",\n"
            + "  \"riskLevel\": \"MEDIUM\",\n"
            + "  \"comments\": [\n"
            + "    {\n"
            + "      \"filePath\": \"code/mas-core/src/main/java/com/vemo/mas/service/aggregate/unified/model/MonitorConfig.java\",\n"
            + "      \"line\": 13,\n"
            + "      \"severity\": \"HIGH\",\n"
            + "      \"category\": \"Project hard rule\",\n"
            + "      \"message\": \"Data classes should use the expected annotations\",\n"
            + "      \"suggestion\": \"Use Getter and Setter\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n"
            + "```");
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        response.setChoices(Collections.singletonList(choice));

        ReviewResponseParser parser = new ReviewResponseParser(new ObjectMapper());
        ReviewSummary summary = parser.parse(response);

        assertEquals(Integer.valueOf(91), summary.getSuggestedScore());
        assertEquals("MonitorConfig needs changes before merge", summary.getBriefSummary());
        assertEquals("MEDIUM", summary.getRiskLevel());
        assertEquals(1, summary.getComments().size());
        assertEquals(Integer.valueOf(13), summary.getComments().get(0).getLine());
        assertEquals("HIGH", summary.getComments().get(0).getSeverity());
    }
}
