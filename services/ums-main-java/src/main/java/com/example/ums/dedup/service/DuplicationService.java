package com.example.ums.dedup.service;

/**
 * 去重聚编排（实现指南 2.1 步骤 5）。
 */
public interface DuplicationService {

    /**
     * 对单条资讯计算指纹并跨源聚类。
     * 匹配成功：当前文章与匹配文章归入同一 duplicate_group_id（保留各来源文章，不删数据）。
     * 无匹配：duplicate_group_id 保持 NULL。
     * 完成后发布 content.finalized {contentId, duplicateGroupId}。
     */
    void aggregate(String articleId);
}
