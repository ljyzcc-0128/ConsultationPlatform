package com.example.ums.push.dto;

import java.util.List;

/**
 * 推送任务列表展示 DTO（含统计与趋势）。
 */
public record PushTaskDto(
        String id,
        String name,
        String target,
        String channel,
        String frequency,
        String status,
        String lastSentAt,
        Stats stats,
        List<TrendPoint> trend) {

    public record Stats(long sent, long delivered, long clicked) {}

    public record TrendPoint(String date, long sent, long clicked) {}
}
