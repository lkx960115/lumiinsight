-- 清洗标记：duplicate/ad/short/masked；counted=0 不计入项目有效评论数
ALTER TABLE review
    ADD COLUMN clean_tags VARCHAR(255) NULL AFTER content_hash,
    ADD COLUMN counted TINYINT NOT NULL DEFAULT 1 AFTER clean_tags;

ALTER TABLE review
    ADD KEY idx_review_counted (project_id, counted);
