CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(120) NOT NULL,
    display_name    VARCHAR(64)  NOT NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    must_change_pwd TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_permission (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    type        VARCHAR(16)  NOT NULL COMMENT 'MENU / BUTTON',
    parent_code VARCHAR(64)  NULL,
    path        VARCHAR(128) NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(128) NOT NULL,
    category     VARCHAR(64)  NULL,
    brand        VARCHAR(64)  NULL,
    main_model   VARCHAR(128) NULL,
    competitors  JSON         NULL,
    keywords     JSON         NULL,
    platforms    JSON         NULL,
    time_start   DATE         NULL,
    time_end     DATE         NULL,
    owner_id     BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project_member (
    project_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_job (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id       BIGINT       NOT NULL,
    type             VARCHAR(32)  NOT NULL DEFAULT 'IMPORT',
    filename         VARCHAR(255) NOT NULL,
    object_key       VARCHAR(512) NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    total_rows       INT          NOT NULL DEFAULT 0,
    success_rows     INT          NOT NULL DEFAULT 0,
    fail_rows        INT          NOT NULL DEFAULT 0,
    error_object_key VARCHAR(512) NULL,
    message          VARCHAR(512) NULL,
    created_by       BIGINT       NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_import_job_project (project_id),
    KEY idx_import_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id    BIGINT        NOT NULL,
    import_job_id BIGINT        NULL,
    platform      VARCHAR(32)   NOT NULL,
    product_id    VARCHAR(128)  NULL,
    review_id     VARCHAR(128)  NOT NULL,
    content       TEXT          NOT NULL,
    review_time   DATETIME      NULL,
    like_count    INT           NOT NULL DEFAULT 0,
    author_hash   VARCHAR(128)  NULL,
    product_name  VARCHAR(255)  NULL,
    source_url    VARCHAR(512)  NULL,
    raw_payload   JSON          NULL,
    content_hash  VARCHAR(64)   NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review_idempotent (project_id, platform, review_id),
    KEY idx_review_project (project_id),
    KEY idx_review_hash (project_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE llm_provider (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    protocol        VARCHAR(32)  NOT NULL DEFAULT 'openai_compat',
    base_url        VARCHAR(255) NOT NULL,
    api_key_cipher  TEXT         NULL,
    api_key_suffix  VARCHAR(8)   NULL,
    extra_headers   JSON         NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    timeout_ms      INT          NOT NULL DEFAULT 60000,
    max_retry       INT          NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE llm_model (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id    BIGINT       NOT NULL,
    model_code     VARCHAR(128) NOT NULL,
    context_length INT          NULL,
    json_mode      TINYINT      NOT NULL DEFAULT 1,
    input_price    DECIMAL(12,6) NULL,
    output_price   DECIMAL(12,6) NULL,
    enabled        TINYINT      NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    KEY idx_llm_model_provider (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE llm_route (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    purpose_code     VARCHAR(64) NOT NULL,
    primary_model_id BIGINT      NULL,
    backup_model_id  BIGINT      NULL,
    enabled          TINYINT     NOT NULL DEFAULT 1,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_llm_route_purpose (purpose_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE aspect_dict (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL,
    sort_no    INT         NOT NULL DEFAULT 0,
    enabled    TINYINT     NOT NULL DEFAULT 1,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_aspect_dict_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NULL,
    username    VARCHAR(64)  NULL,
    action      VARCHAR(64)  NOT NULL,
    resource    VARCHAR(64)  NULL,
    detail      TEXT         NULL,
    ip          VARCHAR(64)  NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_role (code, name) VALUES
    ('admin', '管理员'),
    ('analyst', '分析员');

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no) VALUES
    ('app:projects', '项目工作台', 'MENU', NULL, '/app/projects', 10),
    ('project:view', '查看项目', 'BUTTON', 'app:projects', NULL, 11),
    ('project:edit', '编辑项目', 'BUTTON', 'app:projects', NULL, 12),
    ('project:delete', '删除项目', 'BUTTON', 'app:projects', NULL, 13),
    ('import:execute', '导入评论', 'BUTTON', 'app:projects', NULL, 14),
    ('review:view', '查看评论', 'BUTTON', 'app:projects', NULL, 15),
    ('admin:users', '用户管理', 'MENU', NULL, '/admin/users', 20),
    ('admin:user:view', '查看用户', 'BUTTON', 'admin:users', NULL, 21),
    ('admin:user:edit', '编辑用户', 'BUTTON', 'admin:users', NULL, 22),
    ('admin:llm', '模型配置', 'MENU', NULL, '/admin/llm', 30),
    ('admin:llm:view', '查看模型配置', 'BUTTON', 'admin:llm', NULL, 31),
    ('admin:llm:edit', '编辑模型配置', 'BUTTON', 'admin:llm', NULL, 32),
    ('admin:jobs', '任务日志', 'MENU', NULL, '/admin/jobs', 40),
    ('admin:job:view', '查看任务', 'BUTTON', 'admin:jobs', NULL, 41),
    ('admin:dicts', '方面词典', 'MENU', NULL, '/admin/dicts', 50),
    ('admin:dict:view', '查看词典', 'BUTTON', 'admin:dicts', NULL, 51),
    ('admin:audit', '操作日志', 'MENU', NULL, '/admin/audit', 60),
    ('admin:audit:view', '查看审计', 'BUTTON', 'admin:audit', NULL, 61);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code = 'admin';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'app:projects','project:view','project:edit','project:delete',
    'import:execute','review:view'
) WHERE r.code = 'analyst';

INSERT INTO llm_provider (name, protocol, base_url, enabled, timeout_ms, max_retry) VALUES
    ('智谱 GLM', 'openai_compat', 'https://open.bigmodel.cn/api/paas/v4', 1, 60000, 1),
    ('DeepSeek', 'openai_compat', 'https://api.deepseek.com', 1, 60000, 1),
    ('OpenAI', 'openai_compat', 'https://api.openai.com/v1', 0, 60000, 1),
    ('通义兼容', 'openai_compat', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 0, 60000, 1),
    ('自定义 OpenAI 兼容', 'openai_compat', 'http://127.0.0.1:11434/v1', 0, 60000, 1);

INSERT INTO llm_model (provider_id, model_code, context_length, json_mode, enabled)
SELECT id, 'glm-4.6', 128000, 1, 1 FROM llm_provider WHERE name = '智谱 GLM';
INSERT INTO llm_model (provider_id, model_code, context_length, json_mode, enabled)
SELECT id, 'deepseek-chat', 64000, 1, 1 FROM llm_provider WHERE name = 'DeepSeek';
INSERT INTO llm_model (provider_id, model_code, context_length, json_mode, enabled)
SELECT id, 'gpt-4o-mini', 128000, 1, 0 FROM llm_provider WHERE name = 'OpenAI';
INSERT INTO llm_model (provider_id, model_code, context_length, json_mode, enabled)
SELECT id, 'qwen-plus', 128000, 1, 0 FROM llm_provider WHERE name = '通义兼容';
INSERT INTO llm_model (provider_id, model_code, context_length, json_mode, enabled)
SELECT id, 'custom-model', 32000, 1, 0 FROM llm_provider WHERE name = '自定义 OpenAI 兼容';

INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'absa', m.id, NULL, 1 FROM llm_model m WHERE m.model_code = 'glm-4.6';
INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'sentiment', m.id, NULL, 1 FROM llm_model m WHERE m.model_code = 'glm-4.6';
INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'report_summary', m.id, NULL, 1 FROM llm_model m WHERE m.model_code = 'glm-4.6';
INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'embedding', m.id, NULL, 1 FROM llm_model m WHERE m.model_code = 'glm-4.6';
INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'spam_classify', m.id, NULL, 1 FROM llm_model m WHERE m.model_code = 'glm-4.6';

INSERT INTO aspect_dict (name, sort_no) VALUES
    ('亮度', 1), ('色温', 2), ('外观', 3), ('材质', 4), ('安装', 5),
    ('质量', 6), ('售后', 7), ('价格', 8), ('物流', 9), ('频闪', 10),
    ('寿命', 11), ('智能联动', 12), ('遥控', 13), ('防水', 14), ('包装', 15),
    ('其它', 99);
