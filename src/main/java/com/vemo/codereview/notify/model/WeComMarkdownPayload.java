package com.vemo.codereview.notify.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeComMarkdownPayload {

    private String msgtype = "markdown";
    private MarkdownPayload markdown = new MarkdownPayload();

    @Getter
    @Setter
    public static class MarkdownPayload {
        private String content;
    }
}

