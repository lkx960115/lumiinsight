-- Docker MySQL 默认 UTC，调用记录的 created_at 比北京时间少 8 小时。只纠正这一批存量。
UPDATE llm_usage
SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);
