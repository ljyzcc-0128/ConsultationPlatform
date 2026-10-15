package com.example.ums.content.service;

import com.example.ums.content.dto.NewsPageDto;
import com.example.ums.content.dto.RelatedContentDto;
import com.example.ums.content.dto.TimelineEventDto;

import java.util.List;

/**
 * 内容搜索与关联服务。
 */
public interface ContentSearchService {

    /**
     * 全文搜索（ngram 全文索引）。
     * q 为空时返回最新时间线（与 /api/news 行为一致）。
     */
    NewsPageDto search(String q, String cursor, int limit);

    /**
     * 查找相关内容：先按 duplicate_group_id 查同组，不足按同 category 补充。
     */
    List<RelatedContentDto> findRelated(String articleId);

    /**
     * 事件时间线：查 change_events 表。
     */
    List<TimelineEventDto> findTimeline(String articleId);
}
