package com.example.ums.ai.consumer;

import com.example.ums.ai.model.ContentParsedMessage;
import com.example.ums.ai.service.AiEnrichService;
import com.example.ums.config.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * content.parsed 消费者（实现指南 2.1 步骤 4：AI 加工）。
 * 失败重试 3 次后进 DLQ（ums.content.parsed.dlq），可重放。
 */
@Component
public class ContentParsedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContentParsedConsumer.class);

    private final AiEnrichService aiEnrichService;
    private final ObjectMapper objectMapper;

    public ContentParsedConsumer(AiEnrichService aiEnrichService, ObjectMapper objectMapper) {
        this.aiEnrichService = aiEnrichService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CONTENT_PARSED)
    public void onContentParsed(String payload) {
        ContentParsedMessage message;
        try {
            message = objectMapper.readValue(payload, ContentParsedMessage.class);
        } catch (Exception e) {
            // 契约外的消息直接进 DLQ，不重试（重试也不会成功）
            throw new IllegalArgumentException("content.parsed 消息解析失败: " + payload, e);
        }
        if (message.contentId() == null || message.contentId().isBlank()) {
            throw new IllegalArgumentException("content.parsed 消息缺少 contentId: " + payload);
        }
        log.info("收到 content.parsed contentId={}", message.contentId());
        aiEnrichService.enrich(message.contentId());
    }
}
