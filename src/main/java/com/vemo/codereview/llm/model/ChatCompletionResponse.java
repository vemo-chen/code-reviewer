package com.vemo.codereview.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatCompletionResponse {

    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    public String getFirstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice firstChoice = choices.get(0);
        if (firstChoice == null || firstChoice.getMessage() == null) {
            return null;
        }
        return firstChoice.getMessage().getContent();
    }

    @Getter
    @Setter
    public static class Choice {
        private Integer index;
        private Message message;
    }

    @Getter
    @Setter
    public static class Message {
        private String role;
        private String content;
    }

    @Getter
    @Setter
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
