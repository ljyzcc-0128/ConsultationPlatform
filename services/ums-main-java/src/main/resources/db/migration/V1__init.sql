-- ============================================================
-- V1 初始迁移：cp_ 前缀四表 + 5 个 POC 信源种子
-- 依据：BESS_ESG_News_Crawler_Source_Config_V1.0.xlsx
--       「Source字段定义」「News Object字段定义」两个 sheet
-- 来源：储能资讯平台一期建表SQL_cp_v1.0.sql（用户已核对确认）
-- 说明：MySQL 8.0+；utf8mb4；时间统一UTC（展示层按用户时区转换）
-- ============================================================

-- 1. cp_source 信源配置表（17字段全覆盖）
CREATE TABLE `cp_source` (
  `source_id`       VARCHAR(32)  NOT NULL COMMENT '数据源唯一ID，如 CN-01；创建后保持稳定',
  `source_name`     VARCHAR(128) NOT NULL COMMENT '数据源显示名称',
  `region`          VARCHAR(64)  NOT NULL COMMENT '数据源主要覆盖区域（媒体覆盖范围，不等于文章所属区域）',
  `source_type`     VARCHAR(64)  NOT NULL COMMENT '来源类型，如 行业媒体/政策聚合；后续类型走统一字典',
  `entry_url`       VARCHAR(768) NOT NULL COMMENT '抓取起始入口（保留配置给定URL，请求时可去utm_*）',
  `crawl_enabled`   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用调度：界面启用=true，停用=false；不代表页面适配完成',
  `crawl_frequency` VARCHAR(32)  NOT NULL DEFAULT '1/day' COMMENT '调度频率；调度时区 Asia/Shanghai',
  `priority`        VARCHAR(8)   NOT NULL DEFAULT 'P1' COMMENT 'P0=最高/P1=常规/P2=低；指开发调度优先级，不等于文章重要性',
  `pagination`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否遍历列表分页；停止条件：无下一页/无新增链接/达上限',
  `fetch_detail`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否抓取文章详情（正文/日期/作者/元数据）',
  `date_filter`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否按发布日期筛选；发布日期缺失保留并待审，不用抓取时间替代',
  `language`        VARCHAR(16)  NOT NULL COMMENT '默认来源语言（BCP 47）；文章实际语言以检测结果为准',
  `category`        JSON         NULL COMMENT '来源主题标签，如 ["BESS","Power Market"]；走统一主题字典',
  `last_crawl_time` DATETIME     NULL COMMENT '上次完整成功抓取时间(UTC)；失败或部分成功不覆盖',
  `crawl_status`    VARCHAR(20)  NOT NULL DEFAULT 'NotRun' COMMENT 'NotRun/Running/Success/PartialSuccess 等',
  `remark`          TEXT         NULL COMMENT '来源适配备注：特殊规则、访问限制、联调说明',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`source_id`),
  KEY `idx_crawl_enabled_priority` (`crawl_enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信源配置表';

-- 2. cp_news 资讯主表（窄表，不含正文与分析字段）
CREATE TABLE `cp_news` (
  `article_id`           CHAR(36)      NOT NULL COMMENT '文章唯一标识UUID；系统生成不可变；重抓更新原记录不新建',
  `source_id`            VARCHAR(32)   NOT NULL COMMENT '抓取来源ID，外键指向cp_source',
  `source_name`          VARCHAR(128)  NOT NULL COMMENT '来源名称快照（采集时复制保留，关联以source_id为准）',
  `title`                VARCHAR(512)  NOT NULL COMMENT '原文标题；保留原文语言，仅清除多余空白，不改写事实',
  `original_url`         VARCHAR(768)  NOT NULL COMMENT '实际详情页URL（非列表入口）；已去跟踪参数',
  `url_hash`             CHAR(64)      NOT NULL COMMENT '规范化URL的SHA-256（同源判重的唯一键落点，绕开URL索引长度限制）',
  `publish_date`         DATE          NULL COMMENT '原文发布日期YYYY-MM-DD（发布地日历日）；不得用更新时间或采集时间替代',
  `publish_time`         DATETIME      NULL COMMENT '发布精确时间UTC（超集字段：爬虫消息含完整ISO datetime，精度高于date）',
  `crawl_date`           DATETIME      NOT NULL COMMENT '最近一次成功采集本文的时间UTC；重抓成功时更新',
  `author`               VARCHAR(128)  NULL COMMENT '原文作者/署名机构；仅明确署名，无署名为NULL，不自动填网站名',
  `country`              JSON          NULL COMMENT '新闻涉及国家/辖区，ISO 3166-1 alpha-2，如["CN"]；EU为扩展值',
  `region`               JSON          NULL COMMENT '新闻涉及地理区域，如 China/Europe/North America',
  `language`             VARCHAR(16)   NOT NULL DEFAULT 'zh-CN' COMMENT '文章实际语言（BCP 47，按正文检测；超集字段，来自消息契约）',
  `category`             JSON          NULL COMMENT '一级主题分类，与source.category同字典；按文章内容分类',
  `sub_category`         JSON          NULL COMMENT '二级专业主题，如 DA/ID/BM/FCR/aFRR/mFRR/RR/Capacity Market',
  `summary`              TEXT          NULL COMMENT '中文摘要（三段式）；审核通过必填',
  `duplicate_group_id`   VARCHAR(64)   NULL COMMENT '跨源同事件/同内容聚类ID；同组共用；保留各来源文章，未归组为NULL',
  `manual_review_status` VARCHAR(20)   NOT NULL DEFAULT 'Pending' COMMENT '人工审核状态：Pending/NeedsReview/Approved/Rejected',
  `task_id`              VARCHAR(64)   NULL COMMENT '采集任务溯源（raw.item.fetched消息task_id；超集字段）',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次入库时间UTC',
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`article_id`),
  UNIQUE KEY `uk_source_urlhash` (`source_id`, `url_hash`),
  KEY `idx_publish_date` (`publish_date`),
  KEY `idx_source_date` (`source_id`, `publish_date`),
  KEY `idx_review_status` (`manual_review_status`),
  KEY `idx_dup_group` (`duplicate_group_id`),
  FULLTEXT KEY `ft_title_summary` (`title`, `summary`) WITH PARSER ngram,
  CONSTRAINT `fk_news_source` FOREIGN KEY (`source_id`) REFERENCES `cp_source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资讯主表';

-- 3. cp_news_content 正文表（1:1 共享主键）
CREATE TABLE `cp_news_content` (
  `news_id`    CHAR(36)   NOT NULL COMMENT '资讯article_id（1:1）',
  `body`       MEDIUMTEXT NULL COMMENT '清洗后原文正文：保留段落/数字/单位，去除导航广告；提取失败为NULL并置待审',
  `created_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`news_id`),
  CONSTRAINT `fk_content_news` FOREIGN KEY (`news_id`)
    REFERENCES `cp_news` (`article_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资讯正文表';

-- 4. cp_news_analysis 政策与影响分析表（1:1，审核通过/AI加工后填充）
CREATE TABLE `cp_news_analysis` (
  `news_id`              CHAR(36)      NOT NULL COMMENT '资讯article_id（1:1）',
  `policy_status`        VARCHAR(32)   NULL COMMENT '政策状态：Proposed/Draft/Consultation/Announced等（政策类审核必填）',
  `policy_name`          VARCHAR(512)  NULL COMMENT '政策正式名称（官方原名）；不明确为NULL，不把新闻标题当政策名',
  `issuing_authority`    JSON          NULL COMMENT '发布/制定机构；联合发布多值；不得把转载媒体当发布机构',
  `effective_date`       DATE          NULL COMMENT '政策生效日期YYYY-MM-DD；仅填明确生效日，不用通过日/发布日替代',
  `bess_relevance`       TEXT          NULL COMMENT '对BESS的影响分析（需求/收益机制/并网/市场准入；标明明确规定/合理推断/行业判断）',
  `jinko_ess_relevance`  TEXT          NULL COMMENT '对Jinko ESS的影响分析（系统集成商及设备商视角：PCS/EMS/BMS、认证、供应链、合规）',
  `importance_score`     TINYINT UNSIGNED NULL COMMENT '重要性分数0-100；评分规则版本化；NULL=未评分（0≠NULL）',
  `ai_model`             VARCHAR(64)   NULL COMMENT 'AI加工模型及版本（溯源用；超集字段）',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`news_id`),
  KEY `idx_policy_status` (`policy_status`),
  CONSTRAINT `fk_analysis_news` FOREIGN KEY (`news_id`)
    REFERENCES `cp_news` (`article_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='政策与影响分析表';

-- 5. 种子数据：5个POC信源（来自xlsx「数据源配置」sheet）
INSERT INTO `cp_source`
  (`source_id`, `source_name`, `region`, `source_type`, `entry_url`, `crawl_enabled`, `crawl_frequency`, `priority`, `language`, `category`, `crawl_status`, `remark`) VALUES
  ('CN-01', '中国储能网 ESCN', '中国', '行业媒体/政策聚合', 'https://www.escn.com.cn/news/564.html', 1, '1/day', 'P0', 'zh-CN', '["BESS", "Policy"]', 'NotRun', '页面为静态HTML，选择器已联调通过（2026-09-18）'),
  ('CN-02', '广东省水力和新能源发电工程学会', '中国', '行业组织/政策聚合', 'https://www.gdshe.org/list/7.html', 1, '1/day', 'P0', 'zh-CN', '["BESS", "Policy"]', 'NotRun', '静态HTML，列表+详情选择器已联调通过'),
  ('INT-01', 'Utility Dive', '海外/美国为主', '能源行业媒体', 'https://www.utilitydive.com/topic/storage/', 1, '1/day', 'P0', 'en', '["BESS", "Power Market"]', 'NotRun', 'WAF按TLS指纹拦截httpx（curl可通），需人工介入联调；注意频率限制'),
  ('INT-02', 'Energy-Storage.news', '全球', '储能行业媒体', 'https://www.energy-storage.news/category/news/', 1, '1/day', 'P0', 'en', '["BESS", "Power Market"]', 'NotRun', 'WordPress架构，选择器已联调通过'),
  ('INT-03', 'eszoneo', '全球/欧洲为主', '储能政策情报', 'https://eszoneo.com/intel/policy-updates', 1, '1/day', 'P0', 'en', '["BESS", "Policy"]', 'NotRun', '列表页JS渲染需Playwright；站点响应慢且不稳定，分页与详情选择器待联调');
