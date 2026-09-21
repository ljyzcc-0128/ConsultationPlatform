package com.example.ums.admin.dto;

/** 信源配置更新请求（只传需要修改的字段，null 字段不更新）。 */
public record SourceUpdateRequest(
        String sourceName,
        Boolean crawlEnabled,
        String crawlFrequency,
        String priority,
        String remark) {
}
