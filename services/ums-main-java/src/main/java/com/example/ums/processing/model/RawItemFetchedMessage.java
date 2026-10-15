package com.example.ums.processing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * raw.item.fetched 消息体（对齐 ums-crawler schema_version 1.0 实际发布格式，
 * 与《Java后端实现指南》2.2 草案不同——按指南末尾约定，以已验证的爬虫消息契约为准）。
 *
 * 字段口径：publish_time 缺失为 null；时间为 UTC ISO 8601；original_url 已归一化（去 utm_*）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawItemFetchedMessage(
        String schema_version,
        String event,
        String task_id,
        String fetched_at,
        Item item
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String source_code,
            String title,
            String body,
            String author,
            String publish_time,
            String original_url,
            String language
    ) {}
}
