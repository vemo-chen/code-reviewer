ALTER TABLE project_profile
    ADD COLUMN excluded_review_branches VARCHAR(2000) NULL AFTER review_branches;
