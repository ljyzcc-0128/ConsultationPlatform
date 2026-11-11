package com.example.ums.content.dto;

/**
 * 信源视图条目：某信源的最新一条资讯 + 该信源入库总数。
 * 情报中心信源卡片展示用。
 */
public record SourceLatestDto(
        NewsListItemDto latest,
        Long total) {
}
