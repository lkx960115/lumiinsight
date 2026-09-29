CREATE TABLE pipeline_job (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    message     VARCHAR(512) NULL,
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_pipeline_job_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no)
SELECT 'pipeline:execute', '触发分析流水线', 'BUTTON', 'app:projects', NULL, 16
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'pipeline:execute');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'pipeline:execute'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE rp.role_id IS NULL AND r.code IN ('admin', 'analyst');
