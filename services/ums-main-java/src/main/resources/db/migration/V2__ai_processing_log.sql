-- ============================================================
-- V2：ai_processing_log AI 加工调用记录（AI-003 可追溯）
-- 每次调用记录 provider/model/耗时/状态；输入只存 SHA-256 摘要，不存原文
-- ============================================================
CREATE TABLE `ai_processing_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `news_id`      CHAR(36)     NOT NULL COMMENT '资讯 article_id',
  `operation`    VARCHAR(32)  NOT NULL COMMENT '加工类型：summarize/classify/translate/extract_entities',
  `provider`     VARCHAR(32)  NOT NULL COMMENT 'AI 供应商：glm 等',
  `model`        VARCHAR(64)  NOT NULL COMMENT '模型及版本',
  `status`       VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAILED/SKIPPED',
  `latency_ms`   INT          NULL COMMENT '调用耗时（毫秒）',
  `input_digest` CHAR(64)     NULL COMMENT '输入内容 SHA-256（溯源；不存原文）',
  `error_summary` VARCHAR(512) NULL COMMENT '失败原因摘要',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_news` (`news_id`),
  KEY `idx_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI加工调用记录';
