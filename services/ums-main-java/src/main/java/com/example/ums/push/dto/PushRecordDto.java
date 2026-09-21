package com.example.ums.push.dto;

/**
 * 推送记录 DTO（对齐前端 PushRecord 字段）。
 */
public record PushRecordDto(
        String id,
        String title,
        String channel,
        String sentAt,
        String status) {
}
