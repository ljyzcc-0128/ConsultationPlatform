package com.example.ums.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 审核队列条目（资讯简要 + 审核状态）。 */
public record ReviewItemDto(
        String articleId,
        String title,
        String sourceId,
        String sourceName,
        LocalDate publishDate,
        String language,
        String category,
        String summary,
        String reviewStatus,
        String reviewComment,
        LocalDateTime updatedAt) {
}
