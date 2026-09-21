package com.example.ums.content.dto;

import java.time.LocalDate;

/**
 * 相关内容项（详情页"相关内容"区块）。
 */
public record RelatedContentDto(
        String articleId,
        String title,
        String sourceName,
        LocalDate publishDate,
        String relevance) {  // HIGH / NORMAL
}
