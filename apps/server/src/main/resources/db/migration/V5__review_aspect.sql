ALTER TABLE review
    ADD COLUMN sentiment VARCHAR(8) NULL AFTER aspect_hits,
    ADD COLUMN sentiment_reason VARCHAR(255) NULL AFTER sentiment,
    ADD COLUMN sentiment_confidence DECIMAL(4,3) NULL AFTER sentiment_reason,
    ADD COLUMN analyze_source VARCHAR(16) NULL AFTER sentiment_confidence;

CREATE TABLE review_aspect (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id  BIGINT        NOT NULL,
    review_id   BIGINT        NOT NULL,
    aspect_name VARCHAR(64)   NOT NULL,
    sentiment   VARCHAR(8)    NOT NULL,
    reason      VARCHAR(255)  NULL,
    confidence  DECIMAL(4,3)  NOT NULL DEFAULT 0.500,
    source      VARCHAR(16)   NOT NULL DEFAULT 'rule',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_aspect_review (review_id),
    KEY idx_review_aspect_project (project_id, aspect_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
