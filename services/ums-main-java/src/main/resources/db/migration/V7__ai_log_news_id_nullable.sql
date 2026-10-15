-- V7：智能问答（operation='qa'）无关联资讯，放开 ai_processing_log.news_id 非空限制
ALTER TABLE `ai_processing_log`
  MODIFY `news_id` CHAR(36) NULL COMMENT '资讯 article_id；qa 问答类调用为 NULL';
