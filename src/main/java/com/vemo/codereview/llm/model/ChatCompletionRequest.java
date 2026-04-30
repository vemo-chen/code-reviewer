package com.vemo.codereview.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatCompletionRequest {

    private String model;
    private List<Message> messages;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @Getter
    @Setter
    public static class Message {
        private String role;
        private String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
