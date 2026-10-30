-- 第三方数据服务（按条计费）：提供方、平台通道、用量记账

CREATE TABLE data_source_provider (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name               VARCHAR(64)   NOT NULL COMMENT '供应商名称',
    protocol           VARCHAR(32)   NOT NULL DEFAULT 'HTTP_JSON' COMMENT '协议，默认 HTTP_JSON',
    base_url           VARCHAR(255)  NOT NULL COMMENT '接口根地址',
    api_key_cipher     TEXT          NULL COMMENT '加密后的 Token',
    api_key_suffix     VARCHAR(8)    NULL COMMENT 'Token 末几位，列表脱敏展示',
    price_per_item     DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '单价（元/条）',
    daily_budget_yuan  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '每日预算上限（元），0 表示不限',
    daily_item_limit   INT           NOT NULL DEFAULT 0 COMMENT '每日条数上限，0 表示不限',
    max_pages_per_run  INT           NOT NULL DEFAULT 20 COMMENT '单次最多翻页数，控制单次花费',
    timeout_ms         INT           NOT NULL DEFAULT 120000 COMMENT '超时毫秒，官方建议 120000',
    max_retry          INT           NOT NULL DEFAULT 1 COMMENT '最大重试次数',
    enabled            TINYINT       NOT NULL DEFAULT 0 COMMENT '是否启用：1是 0否',
    remark             VARCHAR(255)  NULL COMMENT '备注',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '是否删除：1是 0否'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方数据服务提供方';

CREATE TABLE data_source_channel (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    provider_id         BIGINT       NOT NULL COMMENT '提供方ID',
    platform            VARCHAR(16)  NOT NULL COMMENT '平台：jd / taobao / douyin / xiaohongshu',
    endpoint_path       VARCHAR(255) NOT NULL COMMENT '接口路径，含已有查询串也可',
    http_method         VARCHAR(8)   NOT NULL DEFAULT 'GET' COMMENT 'HTTP 方法',
    request_template    TEXT         NULL COMMENT '参数模板，支持商品ID、页码、起始时间占位变量',
    review_mapping      TEXT         NOT NULL COMMENT '响应字段映射：字段名到 JSONPath',
    items_path          VARCHAR(255) NOT NULL COMMENT '列表节点 JSONPath，如 $.data',
    page_size           INT          NOT NULL DEFAULT 20 COMMENT '每页条数，0 表示由供应商决定',
    supports_competitor TINYINT      NOT NULL DEFAULT 1 COMMENT '是否支持任意商品（竞品）：1是 0否',
    enabled             TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1是 0否',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_channel (provider_id, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提供方按平台接入配置';

CREATE TABLE data_source_usage (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    provider_id   BIGINT        NOT NULL COMMENT '提供方ID',
    channel_id    BIGINT        NULL COMMENT '通道ID',
    project_id    BIGINT        NULL COMMENT '项目ID',
    platform      VARCHAR(16)   NULL COMMENT '平台',
    run_id        BIGINT        NULL COMMENT '关联导入任务ID',
    request_count INT           NOT NULL DEFAULT 0 COMMENT '请求次数',
    item_count    INT           NOT NULL DEFAULT 0 COMMENT '供应商返回条数（计费口径）',
    cost_yuan     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本次花费（元）',
    success       TINYINT       NOT NULL DEFAULT 1 COMMENT '是否成功：1是 0否',
    detail        VARCHAR(255)  NULL COMMENT '失败原因或备注',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    KEY idx_usage_day (provider_id, created_at),
    KEY idx_usage_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据服务用量与花费记账';

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no) VALUES
    ('admin:datasource', '数据服务', 'MENU', NULL, '/admin/datasource', 35),
    ('admin:datasource:view', '查看数据服务', 'BUTTON', 'admin:datasource', NULL, 36),
    ('admin:datasource:edit', '编辑数据服务', 'BUTTON', 'admin:datasource', NULL, 37);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'admin:datasource', 'admin:datasource:view', 'admin:datasource:edit'
) WHERE r.code = 'admin';
