package com.example.ums.supplychain.consumer;

import com.example.ums.config.RabbitMqConfig;
import com.example.ums.supplychain.model.QuoteMessage;
import com.example.ums.supplychain.service.SupplyChainIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * supplychain.smm.fetched 消费者（供应链采集链路加工端：校验→落库→异动→采集日志）。
 *
 * 失败处理与资讯链路一致：抛异常 → 本地重试 3 次（指数退避）→ reject 进死信队列
 * ums.supplychain.smm.dlq，可在管理端重放。
 */
@Component
@RequiredArgsConstructor
public class SmmQuoteConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmmQuoteConsumer.class);

    private final ObjectMapper objectMapper;
    private final SupplyChainIngestService ingestService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_SUPPLYCHAIN_SMM)
    public void onSmmFetched(String payload) {
        QuoteMessage message = parse(payload);
        log.info("[supplychain] 收到SMM报价消息 task_id={} entries={} failed={}",
                message.task_id(),
                message.entries() == null ? 0 : message.entries().size(),
                message.failed());
        ingestService.ingestSmm(message);
    }

    private QuoteMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, QuoteMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("supplychain.smm.fetched 消息解析失败: " + payload, e);
        }
    }
}
