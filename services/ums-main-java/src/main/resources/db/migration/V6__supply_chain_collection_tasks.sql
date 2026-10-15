-- ============================================================
-- V6 供应链看板 + 采集任务记录
-- 指标对象（indicator_points + series）+ 库存/贸易/供应商动态 + collection_tasks
-- 枚举码统一英文大写；时间统一UTC（展示层按用户时区转换）
-- ============================================================

-- 1. cp_indicator_series 指标序列元数据
CREATE TABLE `cp_indicator_series` (
  `code`         VARCHAR(64)   NOT NULL COMMENT '指标编码（唯一键）',
  `name`         VARCHAR(128)  NOT NULL COMMENT '指标显示名称',
  `unit`         VARCHAR(32)   NOT NULL COMMENT '单位',
  `data_scope`   VARCHAR(32)   NOT NULL DEFAULT 'EXTERNAL_MARKET_DATA' COMMENT 'EXTERNAL_MARKET_DATA/INTERNAL',
  `source_name`  VARCHAR(128)  NOT NULL COMMENT '数据来源名称',
  `latest_value` DECIMAL(16,4) NOT NULL COMMENT '最新值',
  `mom_change`   DECIMAL(8,2)  NOT NULL COMMENT '环比变化百分比',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`code`),
  KEY `idx_scope` (`data_scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指标序列元数据';

-- 2. cp_indicator_point 指标时序数据点
CREATE TABLE `cp_indicator_point` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `series_code` VARCHAR(64)   NOT NULL COMMENT '关联指标编码',
  `point_date`  DATE          NOT NULL COMMENT '数据点日期',
  `value`       DECIMAL(16,4) NOT NULL COMMENT '数据点值',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_series_date` (`series_code`, `point_date`),
  CONSTRAINT `fk_ip_series` FOREIGN KEY (`series_code`) REFERENCES `cp_indicator_series` (`code`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指标时序数据点';

-- 3. cp_inventory_series 库存看板序列（组合图：柱状+折线）
CREATE TABLE `cp_inventory_series` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(128)  NOT NULL COMMENT '序列名称',
  `chart_type`  VARCHAR(16)   NOT NULL COMMENT '图表类型：bar/line',
  `unit`        VARCHAR(32)   NOT NULL COMMENT '单位',
  `data_scope`  VARCHAR(32)   NOT NULL DEFAULT 'EXTERNAL_MARKET_DATA' COMMENT 'EXTERNAL_MARKET_DATA/INTERNAL',
  `source_name` VARCHAR(128)  NOT NULL COMMENT '数据来源名称',
  `data`        JSON          NOT NULL COMMENT '数值数组',
  `months`      JSON          NOT NULL COMMENT '月份标签数组（多序列共享，按行冗余存储）',
  `sort_order`  INT           NOT NULL DEFAULT 0 COMMENT '展示排序',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存看板序列';

-- 4. cp_trade_record 贸易物流记录
CREATE TABLE `cp_trade_record` (
  `id`            VARCHAR(64)   NOT NULL COMMENT '提单号',
  `goods`         VARCHAR(512)  NOT NULL COMMENT '货物描述',
  `counterparty`  VARCHAR(128)  NOT NULL COMMENT '交易对手',
  `route`         VARCHAR(256)  NOT NULL COMMENT '航线',
  `trade_date`    DATE          NOT NULL COMMENT '提单日期',
  `status`        VARCHAR(32)   NOT NULL COMMENT '在途/已到港/清关中/已完成',
  `timeline`      JSON          NOT NULL COMMENT '时间线事件数组 [{time,event}]',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='贸易物流记录';

-- 5. cp_supplier_update 供应商动态
CREATE TABLE `cp_supplier_update` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `supplier_name` VARCHAR(128) NOT NULL COMMENT '供应商名称',
  `update_type`  VARCHAR(64)   NOT NULL COMMENT '扩产/减产/停产/信用风险上升/信用风险下降/新品发布',
  `risk_level`   VARCHAR(16)   NOT NULL COMMENT 'high/medium/low',
  `update_date`  DATE          NOT NULL COMMENT '动态日期',
  `description`  TEXT          NOT NULL COMMENT '动态描述',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_date` (`update_date`),
  KEY `idx_risk` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商动态';

-- 6. cp_collection_task 采集任务记录
CREATE TABLE `cp_collection_task` (
  `task_id`     VARCHAR(64)   NOT NULL COMMENT '采集任务ID',
  `source_id`   VARCHAR(32)   NOT NULL COMMENT '关联信源ID',
  `source_name` VARCHAR(128)  NOT NULL COMMENT '信源名称快照',
  `trigger_type` VARCHAR(16)  NOT NULL COMMENT 'MANUAL/SCHEDULED',
  `started_at`  DATETIME      NOT NULL COMMENT '开始时间UTC',
  `finished_at` DATETIME      NULL COMMENT '结束时间UTC',
  `fetched`     INT           NOT NULL DEFAULT 0 COMMENT '抓取条数',
  `published`   INT           NOT NULL DEFAULT 0 COMMENT '入库条数',
  `failed`      INT           NOT NULL DEFAULT 0 COMMENT '失败条数',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/PARTIAL/FAILED',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`task_id`),
  KEY `idx_source` (`source_id`),
  KEY `idx_started` (`started_at`),
  CONSTRAINT `fk_ct_source` FOREIGN KEY (`source_id`) REFERENCES `cp_source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采集任务记录';

-- ============================================================
-- 种子数据：指标序列元数据（6 条）
-- ============================================================
INSERT INTO `cp_indicator_series`
  (`code`, `name`, `unit`, `data_scope`, `source_name`, `latest_value`, `mom_change`) VALUES
  ('lic-lc', '电池级碳酸锂', '万元/吨', 'EXTERNAL_MARKET_DATA', 'SMM', 8.6200, 2.30),
  ('lfp-cathode', '磷酸铁锂正极材料', '万元/吨', 'EXTERNAL_MARKET_DATA', 'SMM', 4.0500, 1.10),
  ('electrolyte', '磷酸铁锂用电解液', '万元/吨', 'EXTERNAL_MARKET_DATA', 'SMM', 2.1800, -0.80),
  ('cu-foil', '6μm 锂电铜箔', '万元/吨', 'EXTERNAL_MARKET_DATA', 'SMM', 9.3500, 0.40),
  ('cell-price', '磷酸铁锂方形电芯（280Ah）', '元/Wh', 'EXTERNAL_MARKET_DATA', 'SMM', 0.3400, 0.90),
  ('jinko-procurement', '晶科 ESS 电芯采购成本指数', '指数', 'INTERNAL', '内部采购系统', 103.2000, 0.60);

-- ============================================================
-- 种子数据：指标时序点（每序列 30 天，基于 latest_value 波动）
-- ============================================================
INSERT INTO `cp_indicator_point` (`series_code`, `point_date`, `value`)
WITH RECURSIVE seq(n) AS (
  SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 29
)
SELECT s.code,
       DATE_SUB(CURDATE(), INTERVAL 29 - seq.n DAY),
       ROUND(s.latest_value + SIN(seq.n / 5) * s.latest_value * 0.01 + (seq.n - 15) * s.latest_value * 0.0002, 4)
FROM cp_indicator_series s
CROSS JOIN seq
ORDER BY s.code, seq.n;

-- ============================================================
-- 种子数据：库存看板序列（3 条，月份共享）
-- ============================================================
INSERT INTO `cp_inventory_series`
  (`name`, `chart_type`, `unit`, `data_scope`, `source_name`, `data`, `months`, `sort_order`) VALUES
  ('碳酸锂社会库存', 'bar', '万吨', 'EXTERNAL_MARKET_DATA', 'SMM',
   '[1.2, 1.35, 1.42, 1.38, 1.5, 1.62, 1.55, 1.48]',
   '["2月","3月","4月","5月","6月","7月","8月","9月"]', 1),
  ('碳酸锂仓单数量', 'bar', '万吨', 'EXTERNAL_MARKET_DATA', '广期所',
   '[0.32, 0.38, 0.41, 0.36, 0.44, 0.51, 0.47, 0.42]',
   '["2月","3月","4月","5月","6月","7月","8月","9月"]', 2),
  ('储能电芯行业排产', 'line', 'GWh', 'EXTERNAL_MARKET_DATA', 'SMM 调研',
   '[18, 21, 23, 22, 25, 28, 30, 27]',
   '["2月","3月","4月","5月","6月","7月","8月","9月"]', 3);

-- ============================================================
-- 种子数据：贸易物流记录（4 条）
-- ============================================================
INSERT INTO `cp_trade_record`
  (`id`, `goods`, `counterparty`, `route`, `trade_date`, `status`, `timeline`) VALUES
  ('BL-JK20260901', '磷酸铁锂电芯 280Ah × 20 柜', 'HiTHIUM 海辰储能', '厦门港 → 韩国釜山港', '2026-09-14', '在途',
   '[{"time":"2026-09-14 10:00","event":"提单签发（BL-JK20260901）"},{"time":"2026-09-14 18:30","event":"厦门港装船完成"},{"time":"2026-09-16 08:00","event":"船舶离港，预计 09-19 抵达釜山"}]'),
  ('BL-JK20260902', '电池级碳酸锂 120 吨', '江西九岭锂业', '宜春 → 江西上饶工厂', '2026-09-17', '已完成',
   '[{"time":"2026-09-17 09:00","event":"提单签发（BL-JK20260902）"},{"time":"2026-09-17 15:00","event":"公路运输发出"},{"time":"2026-09-18 11:20","event":"到厂验收合格，入库"}]'),
  ('BL-JK20260903', '储能变流器 PCS 3.45MW × 8 台', '上能电气', '无锡 → 越南海防港', '2026-09-18', '清关中',
   '[{"time":"2026-09-18 08:00","event":"提单签发（BL-JK20260903）"},{"time":"2026-09-18 14:00","event":"上海洋山港装船"},{"time":"2026-09-19 09:30","event":"海防港到港，清关资料已提交"}]'),
  ('BL-JK20260904', '电解液 60 吨', '天赐材料', '广州 → 江西上饶工厂', '2026-09-19', '在途',
   '[{"time":"2026-09-19 07:00","event":"提单签发（BL-JK20260904）"},{"time":"2026-09-19 09:00","event":"危化品运输车辆发出"}]');

-- ============================================================
-- 种子数据：供应商动态（5 条）
-- ============================================================
INSERT INTO `cp_supplier_update`
  (`supplier_name`, `update_type`, `risk_level`, `update_date`, `description`) VALUES
  ('江西九岭锂业', '信用风险上升', 'high', '2026-09-18', '票据逾期记录新增 2 条，账期从 60 天压缩至 30 天的建议已提交采购评审。'),
  ('HiTHIUM 海辰储能', '扩产', 'low', '2026-09-17', '厦门基地四期投产，280Ah 电芯产能新增 10GWh，长协供货能力增强。'),
  ('天赐材料', '减产', 'medium', '2026-09-16', '电解液产线例行检修 10 天，Q4 供货窗口紧张，建议提前锁定排产。'),
  ('上能电气', '新品发布', 'low', '2026-09-15', '发布 350kW 组串式储能 PCS，效率提升至 98.5%，可纳入下一批方案选型。'),
  ('中建材（铜箔）', '停产', 'high', '2026-09-14', '因环保督察 6μm 产线停产 2 周，铜箔现货紧张，已启动备用供应商认证。');

-- ============================================================
-- 种子数据：采集任务记录（5 条，与前端 mock 对齐）
-- ============================================================
INSERT INTO `cp_collection_task`
  (`task_id`, `source_id`, `source_name`, `trigger_type`, `started_at`, `finished_at`, `fetched`, `published`, `failed`, `status`) VALUES
  ('70ce6804', 'CN-01', '中国储能网', 'MANUAL', '2026-09-18 13:39:29', '2026-09-18 13:39:44', 3, 3, 0, 'SUCCESS'),
  ('0a031b8d', 'INT-02', 'Energy-Storage.news', 'MANUAL', '2026-09-18 13:40:02', '2026-09-18 13:40:09', 2, 2, 0, 'SUCCESS'),
  ('a48089ce', 'CN-02', '广东水力学会', 'SCHEDULED', '2026-09-18 15:15:48', '2026-09-18 15:16:00', 2, 2, 0, 'SUCCESS'),
  ('c3e94d22', 'INT-01', 'Utility Dive', 'SCHEDULED', '2026-09-18 12:00:00', '2026-09-18 12:00:31', 0, 0, 1, 'FAILED'),
  ('f10a77b3', 'INT-03', 'eszoneo', 'MANUAL', '2026-09-18 11:20:10', '2026-09-18 11:21:10', 5, 3, 2, 'PARTIAL');
