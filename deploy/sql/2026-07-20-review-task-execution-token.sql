ALTER TABLE code_review_task
    ADD COLUMN execution_token VARCHAR(64) NULL AFTER next_retry_at;
