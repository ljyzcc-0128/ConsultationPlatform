package com.example.ums.content.dto;

import java.time.LocalDateTime;

/**
 * 事件时间线项（详情页"事件时间线"区块）。
 */
public record TimelineEventDto(
        LocalDateTime detectedAt,
        String eventType,    // FIRST_DETECTED / HEAT_UP / MAJOR_CHANGE / STABLE_TRACKING
        String description) {
}
