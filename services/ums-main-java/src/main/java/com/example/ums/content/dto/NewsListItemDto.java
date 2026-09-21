package com.example.ums.content.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间线列表项（窄表，不含正文）。
 */
public record NewsListItemDto(
        String articleId,
        String sourceId,
        String sourceName,
        String title,
        String originalUrl,
        LocalDate publishDate,
        LocalDateTime publishTime,
        String author,
        String language,
        List<String> category,
        String summary,
        String duplicateGroupId,
        String manualReviewStatus) {
}
