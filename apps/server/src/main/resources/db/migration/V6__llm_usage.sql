CREATE TABLE llm_usage (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    purpose_code       VARCHAR(64)  NOT NULL,
    provider_id        BIGINT       NULL,
    model_id           BIGINT       NULL,
    model_code         VARCHAR(128) NULL,
    job_id             BIGINT       NULL,
    project_id         BIGINT       NULL,
    prompt_tokens      INT          NOT NULL DEFAULT 0,
    completion_tokens  INT          NOT NULL DEFAULT 0,
    total_tokens       INT          NOT NULL DEFAULT 0,
    success            TINYINT      NOT NULL DEFAULT 1,
    detail             VARCHAR(255) NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_llm_usage_created (created_at),
    KEY idx_llm_usage_purpose (purpose_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
