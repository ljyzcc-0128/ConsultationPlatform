package com.example.ums.dedup.consumer;

import com.example.ums.config.RabbitMqConfig;
import com.example.ums.dedup.service.DuplicationService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * content.processed 消费者（实现指南 2.1 步骤 5：去重聚合）。
 * 失败重试 3 次后进 DLQ（ums.content.processed.dlq）。
 */
@Component
public class ContentProcessedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContentProcessedConsumer.class);

    private final DuplicationService duplicationService;
    private final ObjectMapper objectMapper;

    public ContentProcessedConsumer(DuplicationService duplicationService, ObjectMapper objectMapper) {
        this.duplicationService = duplicationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CONTENT_PROCESSED)
    public void onContentProcessed(String payload) {
        String contentId;
        try {
            JsonNode node = objectMapper.readTree(payload);
            contentId = node.path("contentId").asText(null);
        } catch (Exception e) {
            throw new IllegalArgumentException("content.processed 消息解析失败: " + payload, e);
        }
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("content.processed 消息缺少 contentId: " + payload);
        }
        log.info("收到 content.processed contentId={}", contentId);
        duplicationService.aggregate(contentId);
    }
}
