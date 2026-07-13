ALTER TABLE code_review_event
    ADD COLUMN mr_state VARCHAR(16) NULL AFTER submit_time,
    ADD COLUMN mr_head_sha VARCHAR(64) NULL AFTER mr_state,
    ADD COLUMN merged_sha VARCHAR(64) NULL AFTER mr_head_sha;
