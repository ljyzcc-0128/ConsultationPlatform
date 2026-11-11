package com.example.ums.ai.dto;

/**
 * AI 政策与影响分析结果（cp_news_analysis 回填数据源）。
 * JSON 解析容错：字段缺失即为 null，由调用方按可空处理。
 *
 * @param policyStatus     政策状态（DRAFT/PUBLISHED/IN_FORCE/AMENDED/NON_POLICY，非政策类为 NON_POLICY）
 * @param policyName       政策/文件名称（非政策类为 null）
 * @param issuingAuthority 发布机构 JSON 数组文本（如 ["国家能源局"]），无发布机构时调用方写 '[]'，字段本身可为 null
 * @param effectiveDate    生效日期（yyyy-MM-dd，原文未明确为 null）
 * @param bessRelevance    与储能行业相关性（HIGH/MEDIUM/LOW）
 * @param jinkoEssRelevance 与晶科储能业务相关性（HIGH/MEDIUM/LOW）
 * @param importanceScore  重要性 1-10
 */
public record AnalysisResult(
        String policyStatus,
        String policyName,
        String issuingAuthority,
        String effectiveDate,
        String bessRelevance,
        String jinkoEssRelevance,
        Integer importanceScore) {
}
