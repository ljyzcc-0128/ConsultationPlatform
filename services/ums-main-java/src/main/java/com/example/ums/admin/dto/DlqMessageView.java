package com.example.ums.admin.dto;

import java.util.Map;

/**
 * 死信消息预览详情（不消费，peek 语义）。
 * properties/headers 来自 RabbitMQ Management API 原始字段，可能为空 Map。
 */
public record DlqMessageView(
        String payload,
        String routingKey,
        boolean redelivered,
        String exchange,
        Map<String, Object> properties,
        Map<String, Object> headers) {
}
