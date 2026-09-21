-- ============================================================
-- V4 管理后台支撑：审计日志表 + 资讯审核意见列
-- 依据：Java后端实现指南 5.2（AOP 审计）/ 5.1 内容质量（审核通过/驳回）
-- ============================================================

-- 审计日志（写入由 AuditLogAspect 切面自动完成，业务代码只加 @Audited 注解）
CREATE TABLE `cp_audit_log` (
  `log_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator`    VARCHAR(64)  NOT NULL DEFAULT 'local-admin' COMMENT '操作人（鉴权上下文；本地未启用鉴权时为默认值）',
  `action`      VARCHAR(64)  NOT NULL COMMENT '操作类型，如 SOURCE_UPDATE / REVIEW_APPROVE',
  `object_type` VARCHAR(64)  NOT NULL COMMENT '对象类型，如 cp_source / cp_news / dlq',
  `object_id`   VARCHAR(128) NULL COMMENT '对象ID（信源ID/文章ID/队列名等）',
  `detail`      JSON         NULL COMMENT '参数与结果摘要（截断存储）',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间UTC',
  PRIMARY KEY (`log_id`),
  KEY `idx_action` (`action`),
  KEY `idx_object` (`object_type`, `object_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理操作审计日志';

-- 审核意见（通过/驳回时的备注，驳回原因落这里）
ALTER TABLE `cp_news`
  ADD COLUMN `review_comment` VARCHAR(1024) NULL COMMENT '审核备注/驳回原因（管理后台操作时填写）' AFTER `manual_review_status`;
