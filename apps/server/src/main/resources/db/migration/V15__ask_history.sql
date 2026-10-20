CREATE TABLE ask_history (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    project_id     BIGINT        NOT NULL COMMENT '项目ID',
    question       VARCHAR(255)  NOT NULL COMMENT '问题',
    answer         TEXT          NOT NULL COMMENT '结论',
    source         VARCHAR(16)   NOT NULL DEFAULT 'rule' COMMENT '来源：llm / rule / empty',
    evidence_json  JSON          NULL COMMENT '对应原评快照',
    created_by     BIGINT        NOT NULL COMMENT '提问人',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    KEY idx_ask_history_project (project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目问原评记录';
