package com.example.ums.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（实现指南 2.2：每个消费者队列配置 DLX）。
 *
 * 与 ums-crawler 共用既有 exchange「ums」（topic, durable）：
 * - 主队列 ums.raw.item.fetched.queue 绑定 raw.item.fetched
 * - 消费失败重试 3 次（application.yml listener.retry）后 reject，
 *   经 DLX「ums.dlx」路由到死信队列，不静默丢失
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_UMS = "ums";
    public static final String ROUTING_RAW_ITEM_FETCHED = "raw.item.fetched";
    public static final String QUEUE_RAW_ITEM_FETCHED = "ums.raw.item.fetched.queue";

    public static final String EXCHANGE_DLX = "ums.dlx";
    public static final String ROUTING_RAW_ITEM_DEAD = "raw.item.fetched.dead";
    public static final String QUEUE_RAW_ITEM_DLQ = "ums.raw.item.fetched.dlq";

    public static final String ROUTING_CONTENT_PARSED = "content.parsed";
    public static final String QUEUE_CONTENT_PARSED = "ums.content.parsed.queue";
    public static final String ROUTING_CONTENT_PARSED_DEAD = "content.parsed.dead";
    public static final String QUEUE_CONTENT_PARSED_DLQ = "ums.content.parsed.dlq";

    public static final String ROUTING_CONTENT_PROCESSED = "content.processed";
    public static final String QUEUE_CONTENT_PROCESSED = "ums.content.processed.queue";
    public static final String ROUTING_CONTENT_PROCESSED_DEAD = "content.processed.dead";
    public static final String QUEUE_CONTENT_PROCESSED_DLQ = "ums.content.processed.dlq";

    /** topic exchange，Python 端已 durable 声明过，参数一致则幂等 */
    @Bean
    public TopicExchange umsExchange() {
        return new TopicExchange(EXCHANGE_UMS, true, false);
    }

    /** 死信交换机（direct） */
    @Bean
    public DirectExchange umsDlx() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    /** 主队列：消费 raw.item.fetched，失败消息转发到 DLX */
    @Bean
    public Queue rawItemFetchedQueue() {
        return QueueBuilder.durable(QUEUE_RAW_ITEM_FETCHED)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(ROUTING_RAW_ITEM_DEAD)
                .build();
    }

    @Bean
    public Binding rawItemFetchedBinding() {
        return BindingBuilder.bind(rawItemFetchedQueue())
                .to(umsExchange())
                .with(ROUTING_RAW_ITEM_FETCHED);
    }

    /** 死信队列：异常消息落点，供管理后台「异常处理」界面展示（ADM-002） */
    @Bean
    public Queue rawItemDlq() {
        return QueueBuilder.durable(QUEUE_RAW_ITEM_DLQ).build();
    }

    @Bean
    public Binding rawItemDeadBinding() {
        return BindingBuilder.bind(rawItemDlq())
                .to(umsDlx())
                .with(ROUTING_RAW_ITEM_DEAD);
    }

    /** AI 加工队列：消费 content.parsed（入库成功后由本服务发布） */
    @Bean
    public Queue contentParsedQueue() {
        return QueueBuilder.durable(QUEUE_CONTENT_PARSED)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(ROUTING_CONTENT_PARSED_DEAD)
                .build();
    }

    @Bean
    public Binding contentParsedBinding() {
        return BindingBuilder.bind(contentParsedQueue())
                .to(umsExchange())
                .with(ROUTING_CONTENT_PARSED);
    }

    /** AI 加工死信队列（重试 3 次仍失败的消息，可重放） */
    @Bean
    public Queue contentParsedDlq() {
        return QueueBuilder.durable(QUEUE_CONTENT_PARSED_DLQ).build();
    }

    @Bean
    public Binding contentParsedDeadBinding() {
        return BindingBuilder.bind(contentParsedDlq())
                .to(umsDlx())
                .with(ROUTING_CONTENT_PARSED_DEAD);
    }

    /** 去重聚队列：消费 content.processed（AI 加工完成后由本服务发布） */
    @Bean
    public Queue contentProcessedQueue() {
        return QueueBuilder.durable(QUEUE_CONTENT_PROCESSED)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(ROUTING_CONTENT_PROCESSED_DEAD)
                .build();
    }

    @Bean
    public Binding contentProcessedBinding() {
        return BindingBuilder.bind(contentProcessedQueue())
                .to(umsExchange())
                .with(ROUTING_CONTENT_PROCESSED);
    }

    /** 去重聚死信队列 */
    @Bean
    public Queue contentProcessedDlq() {
        return QueueBuilder.durable(QUEUE_CONTENT_PROCESSED_DLQ).build();
    }

    @Bean
    public Binding contentProcessedDeadBinding() {
        return BindingBuilder.bind(contentProcessedDlq())
                .to(umsDlx())
                .with(ROUTING_CONTENT_PROCESSED_DEAD);
    }
}
