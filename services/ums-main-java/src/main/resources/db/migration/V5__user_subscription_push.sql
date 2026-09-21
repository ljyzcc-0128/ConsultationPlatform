-- ============================================================
-- V5 用户体系 + 我的情报 + 订阅推送链路
-- SSO 预留：cp_user.provider / external_id
-- 枚举码统一英文大写，前端用 *_TEXT 映射表翻译
-- ============================================================

-- 1. cp_user 用户表
CREATE TABLE `cp_user` (
  `user_id`      CHAR(36)     NOT NULL COMMENT '用户ID（UUID，系统生成）',
  `username`     VARCHAR(64)  NOT NULL COMMENT '登录用户名（LOCAL 模式唯一；SSO 模式可空）',
  `display_name` VARCHAR(128) NULL COMMENT '显示名',
  `email`        VARCHAR(255) NULL COMMENT '邮箱（推送渠道用）',
  `roles`        JSON         NOT NULL COMMENT '角色码数组，如 ["USER","ADMIN"]',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `provider`     VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/SSO（认证来源）',
  `external_id`  VARCHAR(128) NULL COMMENT 'SSO 映射外部ID（LOCAL 为 NULL）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username_provider` (`username`, `provider`),
  UNIQUE KEY `uk_external_provider` (`external_id`, `provider`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 2. cp_subscription 订阅规则表
CREATE TABLE `cp_subscription` (
  `id`                CHAR(36)     NOT NULL COMMENT '订阅规则ID（UUID）',
  `user_id`           CHAR(36)     NOT NULL,
  `subscription_type` VARCHAR(32)  NOT NULL COMMENT 'CONTENT_DOMAIN/THEME/COMPANY/KEYWORD/INDICATOR/EVENT',
  `value`             VARCHAR(512) NOT NULL COMMENT '订阅值（主题名/企业名/关键词等）',
  `frequency`         VARCHAR(16)  NOT NULL DEFAULT 'INSTANT' COMMENT 'INSTANT/DAILY/WEEKLY',
  `channel`           VARCHAR(16)  NOT NULL DEFAULT 'IN_APP' COMMENT 'IN_APP/EMAIL/WECHAT',
  `enabled`           TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_enabled_type` (`enabled`, `subscription_type`),
  CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `cp_user`(`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户订阅规则';

-- 3. change_events 变化事件表（Feed 重大变化来源 + 详情页时间线）
CREATE TABLE `change_events` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `article_id`  CHAR(36)     NULL COMMENT '关联资讯（可空：事件可不绑文章）',
  `event_type`  VARCHAR(32)  NOT NULL COMMENT 'FIRST_DETECTED/HEAT_UP/MAJOR_CHANGE/STABLE_TRACKING',
  `detected_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间UTC',
  `description` VARCHAR(1024) NULL COMMENT '事件描述',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_article` (`article_id`),
  KEY `idx_detected` (`detected_at`),
  CONSTRAINT `fk_ce_news` FOREIGN KEY (`article_id`) REFERENCES `cp_news`(`article_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变化事件';

-- 4. push_tasks 推送任务表
CREATE TABLE `push_tasks` (
  `id`           CHAR(36)     NOT NULL COMMENT '任务ID（UUID）',
  `name`         VARCHAR(128) NOT NULL COMMENT '任务名称',
  `target_desc`  VARCHAR(512) NULL COMMENT '目标人群描述（展示用）',
  `channel`      VARCHAR(16)  NOT NULL COMMENT 'IN_APP/EMAIL/WECHAT',
  `frequency`    VARCHAR(16)  NOT NULL COMMENT 'INSTANT/DAILY/WEEKLY',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/PAUSED/FINISHED',
  `last_sent_at` DATETIME     NULL COMMENT '上次发送时间UTC',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推送任务';

-- 5. push_messages 推送消息表（高追加量）
CREATE TABLE `push_messages` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    CHAR(36)     NOT NULL,
  `task_id`    CHAR(36)     NULL,
  `article_id` CHAR(36)     NULL,
  `channel`    VARCHAR(16)  NOT NULL COMMENT 'IN_APP/EMAIL/WECHAT',
  `title`      VARCHAR(512) NOT NULL,
  `body`       TEXT         NULL,
  `sent_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间UTC',
  `status`     VARCHAR(16)  NOT NULL DEFAULT 'SENT' COMMENT 'SENT/DELIVERED/CLICKED/FAILED',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间UTC',
  PRIMARY KEY (`id`),
  KEY `idx_user_sent` (`user_id`, `sent_at`),
  KEY `idx_task` (`task_id`),
  CONSTRAINT `fk_pm_user` FOREIGN KEY (`user_id`) REFERENCES `cp_user`(`user_id`),
  CONSTRAINT `fk_pm_task` FOREIGN KEY (`task_id`) REFERENCES `push_tasks`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推送消息';

-- 6. cp_read_status 已读状态（复合主键）
CREATE TABLE `cp_read_status` (
  `user_id`    CHAR(36) NOT NULL,
  `article_id` CHAR(36) NOT NULL,
  `read_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间UTC',
  PRIMARY KEY (`user_id`, `article_id`),
  KEY `idx_user_read` (`user_id`, `read_at`),
  CONSTRAINT `fk_rs_user` FOREIGN KEY (`user_id`) REFERENCES `cp_user`(`user_id`),
  CONSTRAINT `fk_rs_news` FOREIGN KEY (`article_id`) REFERENCES `cp_news`(`article_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已读状态';

-- 7. 种子：1 个 LOCAL admin 用户
INSERT INTO `cp_user`
  (`user_id`, `username`, `display_name`, `email`, `roles`, `status`, `provider`) VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin', '系统管理员', 'admin@jinkoess.com',
   '["USER","ADMIN"]', 'ACTIVE', 'LOCAL');
