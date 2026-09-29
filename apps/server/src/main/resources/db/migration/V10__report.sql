CREATE TABLE report (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    project_id  BIGINT        NOT NULL COMMENT '项目ID',
    title       VARCHAR(255)  NOT NULL COMMENT '报告标题',
    status      VARCHAR(32)   NOT NULL COMMENT '状态：READY / FAILED',
    body_json   JSON          NOT NULL COMMENT '章节与结论，每条结论带 evidenceIds',
    snapshot_json JSON        NULL COMMENT '声量、情感、方面统计快照',
    message     VARCHAR(512)  NULL COMMENT '生成说明或失败原因',
    created_by  BIGINT        NOT NULL COMMENT '生成人用户ID',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_report_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单品口碑报告';

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no)
SELECT 'app:reports', '报告', 'MENU', NULL, '/app/reports', 15
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'app:reports');

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no)
SELECT 'report:view', '查看报告', 'BUTTON', 'app:reports', NULL, 16
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'report:view');

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no)
SELECT 'report:generate', '生成报告', 'BUTTON', 'app:reports', NULL, 17
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'report:generate');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('app:reports', 'report:view', 'report:generate')
WHERE r.code IN ('admin', 'analyst')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
