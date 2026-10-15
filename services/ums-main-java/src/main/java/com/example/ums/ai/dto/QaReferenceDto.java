package com.example.ums.ai.dto;

import java.time.LocalDate;

/**
 * 问答引用来源（回答所基于的资讯条目，前端可据此跳转详情/原文）。
 */
public record QaReferenceDto(String articleId, String title, String sourceName,
                             String originalUrl, LocalDate publishDate) {
}
