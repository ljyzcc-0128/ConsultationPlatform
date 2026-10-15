package com.example.ums.processing.consumer;

import com.example.ums.config.RabbitMqConfig;
import com.example.ums.processing.model.RawItemFetchedMessage;
import com.example.ums.processing.service.NewsIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * raw.item.fetched 消费者（采集加工链路步骤 3：解析清洗后入库）。
 *
 * 失败处理：抛异常 → 本地重试 3 次（指数退避）→ reject 进死信队列 ums.raw.item.fetched.dlq。
 * content.parsed 在入库成功后发布（AI 加工链路入口，由 ai 包消费）。
 */
@Component
public class RawItemFetchedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawItemFetchedConsumer.class);

    private final ObjectMapper objectMapper;
    private final NewsIngestService newsIngestService;
    private final RabbitTemplate rabbitTemplate;

    public RawItemFetchedConsumer(ObjectMapper objectMapper,
                                  NewsIngestService newsIngestService,
                                  RabbitTemplate rabbitTemplate) {
        this.objectMapper = objectMapper;
        this.newsIngestService = newsIngestService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_RAW_ITEM_FETCHED)
    public void onRawItemFetched(String payload) {
        RawItemFetchedMessage message = parse(payload);
        String articleId = newsIngestService.ingest(message);
        publishContentParsed(articleId, message.item().source_code());
    }

    private RawItemFetchedMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, RawItemFetchedMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("raw.item.fetched 消息解析失败: " + payload, e);
        }
    }

    private void publishContentParsed(String articleId, String sourceCode) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "event", "content.parsed",
                    "contentId", articleId,
                    "sourceId", sourceCode));
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS,
                    RabbitMqConfig.ROUTING_CONTENT_PARSED, body);
        } catch (Exception e) {
            // 发布失败不阻断入库主流程（链路解耦原则），后续阶段可加补偿
            log.warn("content.parsed 发布失败 articleId={}", articleId, e);
        }
    }
}
