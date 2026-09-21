package com.example.ums.admin.dto;

import java.time.LocalDateTime;

/** 采集任务记录。 */
public record CollectionTaskDto(
        String taskId,
        String sourceId,
        String sourceName,
        String triggerType,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer fetched,
        Integer published,
        Integer failed,
        String status) {
}
