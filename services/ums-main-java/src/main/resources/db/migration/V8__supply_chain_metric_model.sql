-- ============================================================
-- V8 供应链指标模型扩展（广期所仓单接入）
-- 指标口径：series 加 methodology_note/region；point 加 subject（多主体拆分预留）
-- 新增：cp_change_events 指标异动事件、cp_sc_collection_task 供应链采集任务记录
-- 说明：SMM/卓创等外部数据源不进信源体系（不建 cp_source 记录），仅用 source_name 快照
-- ============================================================

-- 1. 指标序列元数据：补口径说明与区域维度（技术设计文档 3.4）
ALTER TABLE `cp_indicator_series`
  ADD COLUMN `methodology_note` VARCHAR(300) NULL COMMENT '口径说明（采集渠道/统计规则）' AFTER `mom_change`,
  ADD COLUMN `region` VARCHAR(50) NULL COMMENT '适用区域（区域价格类指标用）' AFTER `methodology_note`;

-- 2. 指标数据点：subject 主体维度，唯一键放宽到 (series_code, point_date, subject)
--    现存 mock 数据 subject 以 ''（序列总量）落位，历史行为不变
ALTER TABLE `cp_indicator_point`
  ADD COLUMN `subject` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '主体维度（仓库/品牌等），序列总量行为空串' AFTER `point_date`,
  DROP KEY `uk_series_date`,
  ADD UNIQUE KEY `uk_series_date_subject` (`series_code`, `point_date`, `subject`);

-- 3. 指标异动事件（技术设计文档 3.5：INDICATOR，OPEN→MERGED/DISMISSED 人工处置）
CREATE TABLE `cp_change_events` (
  `event_code`     VARCHAR(96)   NOT NULL COMMENT '事件编码（唯一键：指标:日期:类型）',
  `object_type`    VARCHAR(32)   NOT NULL COMMENT '对象类型：INDICATOR/NEWS/SUPPLIER',
  `object_id`      VARCHAR(64)   NOT NULL COMMENT '对象ID（指标编码等）',
  `change_type`    VARCHAR(32)   NOT NULL COMMENT '异动类型：WBILL_CHANGE/PRICE_CHANGE',
  `before_summary` VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '异动前摘要',
  `after_summary`  VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '异动后摘要（含明细归因）',
  `detected_at`    DATETIME      NOT NULL COMMENT '检出时间UTC',
  `severity`       VARCHAR(16)   NOT NULL DEFAULT 'LOW' COMMENT 'HIGH/MEDIUM/LOW（按变动幅度分档）',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/MERGED/DISMISSED',
  `source_name`    VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '数据来源名称快照（无外键）',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`event_code`),
  KEY `idx_object` (`object_type`, `object_id`),
  KEY `idx_detected` (`detected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指标异动事件';

-- 4. 供应链采集任务记录（与资讯链路 cp_collection_task 分离：来源不走信源体系，无外键）
CREATE TABLE `cp_sc_collection_task` (
  `task_id`     VARCHAR(64)   NOT NULL COMMENT '采集任务ID（Python 侧生成）',
  `source_name` VARCHAR(128)  NOT NULL COMMENT '数据来源名称快照（如 广期所）',
  `trigger_type` VARCHAR(16)  NOT NULL COMMENT 'MANUAL/SCHEDULED',
  `started_at`  DATETIME      NOT NULL COMMENT '开始时间UTC',
  `finished_at` DATETIME      NULL COMMENT '结束时间UTC',
  `fetched`     INT           NOT NULL DEFAULT 0 COMMENT '抓取成功品种数',
  `saved`       INT           NOT NULL DEFAULT 0 COMMENT '入库成功指标数',
  `failed`      INT           NOT NULL DEFAULT 0 COMMENT '失败条数',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/PARTIAL/FAILED',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`task_id`),
  KEY `idx_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应链采集任务记录';
