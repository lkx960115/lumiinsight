CREATE TABLE alert_history (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    kind            VARCHAR(32)  NOT NULL COMMENT '类型：neg_spike / new_pain / volume_drop',
    aspect          VARCHAR(64)  NOT NULL COMMENT '方面或声量',
    recent_label    VARCHAR(64)  NOT NULL COMMENT '对照近期',
    previous_label  VARCHAR(64)  NOT NULL COMMENT '对照前期',
    recent_neg      INT          NOT NULL DEFAULT 0 COMMENT '近期负向条数',
    previous_neg    INT          NOT NULL DEFAULT 0 COMMENT '前期负向条数',
    recent_volume   INT          NOT NULL DEFAULT 0 COMMENT '近期有效评论',
    previous_volume INT          NOT NULL DEFAULT 0 COMMENT '前期有效评论',
    summary         TEXT         NOT NULL COMMENT '结论',
    source          VARCHAR(16)  NOT NULL DEFAULT 'rule' COMMENT '来源：llm / rule',
    evidence_json   JSON         NULL COMMENT '对应原评快照',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    UNIQUE KEY uk_alert_window (project_id, kind, aspect, recent_label, previous_label),
    KEY idx_alert_history_project (project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目口碑预警对照记录';
