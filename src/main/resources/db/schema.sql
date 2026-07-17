CREATE TABLE IF NOT EXISTS code_review_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_platform VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    project_name VARCHAR(128),
    object_id VARCHAR(128) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64),
    operator_name VARCHAR(64),
    submit_branch VARCHAR(255),
    submit_time DATETIME,
    mr_state VARCHAR(16),
    mr_head_sha VARCHAR(64),
    merged_sha VARCHAR(64),
    idempotent_key VARCHAR(128) NOT NULL,
    payload_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_review_event_idempotent_key (idempotent_key),
    KEY idx_review_event_project (project_id),
    KEY idx_review_event_status (status),
    KEY idx_review_event_submit_time (submit_time)
);

CREATE TABLE IF NOT EXISTS code_review_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    source_platform VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    target_title VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    fix_status VARCHAR(32),
    fix_submitted_by BIGINT,
    fix_submitted_at DATETIME,
    fix_reviewed_by BIGINT,
    fix_reviewed_at DATETIME,
    fix_review_comment VARCHAR(1000),
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME,
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_review_task_project_type_target (project_id, task_type, target_id),
    KEY idx_review_task_event (event_id),
    KEY idx_review_task_status (status),
    KEY idx_review_task_project (project_id),
    KEY idx_review_task_fix_status (fix_status),
    KEY idx_review_task_project_fix_status (project_id, fix_status)
);

CREATE TABLE IF NOT EXISTS code_review_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    branch_scope VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    created_by_name VARCHAR(64),
    total_commit_count INT NOT NULL DEFAULT 0,
    created_task_count INT NOT NULL DEFAULT 0,
    retried_task_count INT NOT NULL DEFAULT 0,
    skipped_reviewed_count INT NOT NULL DEFAULT 0,
    skipped_running_count INT NOT NULL DEFAULT 0,
    skipped_failed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(512),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    finished_at DATETIME,
    KEY idx_review_batch_project (project_id),
    KEY idx_review_batch_status (status),
    KEY idx_review_batch_created_by (created_by)
);

CREATE TABLE IF NOT EXISTS code_review_batch_task_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    submit_branch VARCHAR(255),
    action_type VARCHAR(32) NOT NULL,
    message VARCHAR(512),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_review_batch_task_rel (batch_id, task_id),
    KEY idx_review_batch_task_rel_batch (batch_id),
    KEY idx_review_batch_task_rel_task (task_id),
    KEY idx_review_batch_task_rel_target (target_id)
);

CREATE TABLE IF NOT EXISTS code_review_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    diff_excerpt TEXT,
    is_skipped TINYINT(1) NOT NULL DEFAULT 0,
    skip_reason VARCHAR(255),
    created_at DATETIME NOT NULL,
    KEY idx_review_file_task (task_id)
);

CREATE TABLE IF NOT EXISTS code_review_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    suggested_score INT,
    deduction_score INT,
    final_score INT,
    summary TEXT,
    brief_summary TEXT,
    score_reason TEXT,
    advice TEXT,
    input_tokens INT,
    output_tokens INT,
    latency_ms BIGINT,
    raw_response TEXT,
    wecom_notify_status VARCHAR(20),
    wecom_notify_attempts INT NOT NULL DEFAULT 0,
    wecom_notified_at DATETIME,
    wecom_notify_error_code VARCHAR(64),
    wecom_notify_error_message VARCHAR(512),
    created_at DATETIME NOT NULL,
    KEY idx_review_result_task (task_id)
);

CREATE TABLE IF NOT EXISTS code_review_comment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    result_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    line_no INT,
    severity VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    suggestion TEXT,
    comment_hash VARCHAR(128) NOT NULL,
    is_posted TINYINT(1) NOT NULL DEFAULT 0,
    posted_at DATETIME,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_review_comment_hash (comment_hash),
    KEY idx_review_comment_result (result_id)
);

CREATE TABLE IF NOT EXISTS llm_model_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    model_name VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    maintainer_project_id BIGINT,
    timeout_ms INT,
    max_tokens INT,
    temperature DECIMAL(5,2),
    thinking_enabled TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_llm_model_enabled (enabled),
    KEY idx_llm_model_scope_type (scope_type),
    KEY idx_llm_model_maintainer_project_id (maintainer_project_id)
);

CREATE TABLE IF NOT EXISTS llm_model_project_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    llm_model_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_llm_model_project_rel (llm_model_id, project_id),
    KEY idx_llm_model_project_rel_project_id (project_id)
);

CREATE TABLE IF NOT EXISTS project_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(128) NOT NULL,
    template_desc VARCHAR(500),
    file_extensions VARCHAR(1000),
    base_review_prompt TEXT,
    created_by BIGINT,
    updated_by BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_project_template_name (template_name)
);

CREATE TABLE IF NOT EXISTS notify_channel_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_type VARCHAR(32) NOT NULL,
    channel_name VARCHAR(64) NOT NULL,
    webhook_url VARCHAR(255) NOT NULL,
    secret VARCHAR(255),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_report_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL,
    project_id BIGINT NOT NULL,
    developer_id VARCHAR(64) NOT NULL,
    commit_count INT NOT NULL DEFAULT 0,
    mr_count INT NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    high_risk_count INT NOT NULL DEFAULT 0,
    summary TEXT,
    created_at DATETIME NOT NULL,
    KEY idx_daily_report_project_date (project_id, report_date)
);

CREATE TABLE IF NOT EXISTS project_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_key VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    source_platform VARCHAR(32) NOT NULL,
    gitlab_project_id BIGINT,
    gitlab_project_url VARCHAR(512),
    gitlab_webhook_token VARCHAR(255),
    review_branches VARCHAR(2000),
    owner_user_id BIGINT,
    template_id BIGINT,
    supported_file_extensions VARCHAR(1000),
    llm_model_id BIGINT,
    ai_review_enabled TINYINT(1) NOT NULL DEFAULT 1,
    review_context_enabled TINYINT(1) NOT NULL DEFAULT 1,
    gitlab_note_enabled TINYINT(1) NOT NULL DEFAULT 1,
    wecom_notify_enabled TINYINT(1) NOT NULL DEFAULT 0,
    wecom_webhook_url VARCHAR(512),
    prompt_content TEXT,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_project_profile_key (project_key),
    UNIQUE KEY uk_project_profile_gitlab_id (source_platform, gitlab_project_id),
    KEY idx_project_profile_active (active)
);

CREATE TABLE IF NOT EXISTS code_review_fix_flow (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    comment VARCHAR(1000),
    created_at DATETIME NOT NULL,
    KEY idx_fix_flow_task (task_id),
    KEY idx_fix_flow_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS developer_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    developer_key VARCHAR(64) NOT NULL,
    developer_name VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_developer_profile_key (developer_key)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    gitlab_username VARCHAR(128),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_gitlab_username (gitlab_username),
    KEY idx_sys_user_role (role),
    KEY idx_sys_user_status (status)
);

CREATE TABLE IF NOT EXISTS sys_user_project_rel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_sys_user_project (user_id, project_id),
    KEY idx_sys_user_project_user (user_id),
    KEY idx_sys_user_project_project (project_id)
);

CREATE TABLE IF NOT EXISTS code_review_comment_code_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    ref VARCHAR(128),
    current_code MEDIUMTEXT,
    suggested_code MEDIUMTEXT,
    start_line INT,
    end_line INT,
    evidence_type VARCHAR(32),
    confidence VARCHAR(32),
    created_at DATETIME NOT NULL,
    KEY idx_comment_code_snapshot_comment (comment_id)
);
