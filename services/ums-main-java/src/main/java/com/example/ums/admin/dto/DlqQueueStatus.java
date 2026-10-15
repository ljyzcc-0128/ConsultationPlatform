package com.example.ums.admin.dto;

/** 死信队列状态。 */
public record DlqQueueStatus(String queue, long messages) {
}
