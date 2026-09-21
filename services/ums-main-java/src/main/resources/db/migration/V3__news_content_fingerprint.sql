-- ============================================================
-- V3：cp_news 增加内容指纹（去重聚步骤 5）
-- content_fingerprint = 归一化(标题+正文) 的 SHA-256，跨源精确判重
-- duplicate_group_id 已在 V1 定义（跨源同事件/同内容聚类，保留各来源文章）
-- ============================================================
ALTER TABLE `cp_news`
  ADD COLUMN `content_fingerprint` CHAR(64) NULL COMMENT '内容指纹：归一化(标题+正文)的SHA-256；跨源精确判重' AFTER `category`,
  ADD KEY `idx_content_fp` (`content_fingerprint`);
