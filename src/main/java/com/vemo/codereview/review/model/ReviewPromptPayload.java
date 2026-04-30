package com.vemo.codereview.review.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReviewPromptPayload {

    private String systemPrompt;
    private String userPrompt;
    private List<PromptFilePayload> files;

    @Getter
    @Setter
    public static class PromptFilePayload {
        private String filePath;
        private String changeType;
        private List<String> diffChunks;
    }
}