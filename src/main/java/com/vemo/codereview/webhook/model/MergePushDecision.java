package com.vemo.codereview.webhook.model;

public enum MergePushDecision {
    SKIP_ALREADY_REVIEWED,
    CREATE_PUSH_REVIEW,
    IGNORE_NO_CODE
}
