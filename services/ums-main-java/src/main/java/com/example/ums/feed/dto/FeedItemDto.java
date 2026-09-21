package com.example.ums.feed.dto;

import java.util.List;

public record FeedItemDto(
        String articleId,
        String title,
        String summary,
        String sourceName,
        String publishDate,
        String publishTime,
        String language,
        List<String> category,
        boolean readStatus,
        String changeType,
        String importance,
        List<String> aiGeneratedFields) {
}
