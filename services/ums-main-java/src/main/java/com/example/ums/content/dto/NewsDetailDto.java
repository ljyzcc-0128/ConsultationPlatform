package com.example.ums.content.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 资讯详情：列表项全部字段 + 正文 + 政策与影响分析（AI 加工后填充，可为空）。
 */
public record NewsDetailDto(
        String articleId,
        String sourceId,
        String sourceName,
        String title,
        String originalUrl,
        LocalDate publishDate,
        java.time.LocalDateTime publishTime,
        String author,
        String language,
        List<String> category,
        String summary,
        String duplicateGroupId,
        String body,
        AnalysisDto analysis) {

    /** cp_news_analysis 1:1（政策与影响分析，AI 加工后填充） */
    public record AnalysisDto(
            String policyStatus,
            String policyName,
            /** 发布机构 JSON 数组文本（如 ["国家能源局"]），原文未提及为 null */
            String issuingAuthority,
            LocalDate effectiveDate,
            String bessRelevance,
            String jinkoEssRelevance,
            Integer importanceScore) {
    }
}
