package com.example.ums.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * content.parsed 消息体（本服务自己发布的链路消息，字段从简：消费者只依赖 contentId）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentParsedMessage(
        String event,
        String contentId,
        String sourceId
) {
    public static final String ROUTING_KEY = "content.parsed";
}
