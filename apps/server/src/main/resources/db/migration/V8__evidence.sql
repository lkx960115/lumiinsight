CREATE TABLE evidence (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id   BIGINT        NOT NULL,
    review_id    BIGINT        NOT NULL,
    aspect_name  VARCHAR(64)   NOT NULL,
    sentiment    VARCHAR(8)    NOT NULL,
    quote        VARCHAR(255)  NULL,
    source       VARCHAR(16)   NOT NULL DEFAULT 'rule',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_evidence_review_aspect (review_id, aspect_name),
    KEY idx_evidence_project_aspect (project_id, aspect_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO evidence (project_id, review_id, aspect_name, sentiment, quote, source, created_at)
SELECT project_id, review_id, aspect_name, MAX(sentiment), MAX(reason), MAX(source), MIN(created_at)
FROM review_aspect
GROUP BY project_id, review_id, aspect_name;
