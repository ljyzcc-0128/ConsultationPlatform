package com.example.ums.admin.dto;

/** 信源新增请求（必填字段校验由 Service 层执行）。 */
public record SourceCreateRequest(
        String sourceId,
        String sourceName,
        String region,
        String sourceType,
        String entryUrl,
        String language,
        Boolean crawlEnabled,
        String crawlFrequency,
        String priority,
        Boolean pagination,
        Boolean fetchDetail,
        Boolean dateFilter,
        String category,
        String remark) {
}
