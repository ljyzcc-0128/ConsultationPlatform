package com.example.ums.admin.dto;

/** 死信消息预览（不消费，peek 语义）。 */
public record DlqMessageView(String payload, String routingKey, boolean redelivered) {
}
