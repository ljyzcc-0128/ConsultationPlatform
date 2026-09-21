package com.example.ums.admin.service.impl;

import com.example.ums.admin.annotation.Audited;
import com.example.ums.admin.dto.DlqMessageView;
import com.example.ums.admin.dto.DlqQueueStatus;
import com.example.ums.admin.service.AdminDlqService;
import com.example.ums.config.RabbitMqConfig;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 死信队列管理实现，走 RabbitMQ Management HTTP API（默认 127.0.0.1:15672）。
 * 重放语义：ackmode=ack_requeue_false 取出（移出 DLQ）→ 逐条以原始 routing key
 * 重新发布到 exchange ums（消费者按正常流程处理，失败仍会回到 DLQ）。
 */
@Service
public class AdminDlqServiceImpl implements AdminDlqService {

    /** DLQ 队列名 → 原始 routing key（白名单，防止操作任意队列） */
    private static final Map<String, String> DLQ_ROUTES = Map.of(
            RabbitMqConfig.QUEUE_RAW_ITEM_DLQ, RabbitMqConfig.ROUTING_RAW_ITEM_FETCHED,
            RabbitMqConfig.QUEUE_CONTENT_PARSED_DLQ, RabbitMqConfig.ROUTING_CONTENT_PARSED,
            RabbitMqConfig.QUEUE_CONTENT_PROCESSED_DLQ, RabbitMqConfig.ROUTING_CONTENT_PROCESSED);

    private static final int REQUEUE_BATCH_MAX = 500;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> MAP_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RabbitTemplate rabbitTemplate;
    private final String mgmtBaseUrl;
    private final String mgmtUser;
    private final String mgmtPassword;
    private final RestClient restClient = RestClient.create();

    public AdminDlqServiceImpl(RabbitTemplate rabbitTemplate,
                               @Value("${ums.mgmt.base-url:http://127.0.0.1:15672}") String mgmtBaseUrl,
                               @Value("${ums.mgmt.user:ums_admin}") String mgmtUser,
                               @Value("${ums.mgmt.password:}") String mgmtPassword) {
        this.rabbitTemplate = rabbitTemplate;
        this.mgmtBaseUrl = mgmtBaseUrl;
        this.mgmtUser = mgmtUser;
        this.mgmtPassword = mgmtPassword;
    }

    @Override
    public List<DlqQueueStatus> queues() {
        List<Map<String, Object>> all = auth(restClient.get()
                .uri(mgmtBaseUrl + "/api/queues"))
                .retrieve().body(MAP_LIST);
        List<DlqQueueStatus> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        for (Map<String, Object> q : all) {
            String name = String.valueOf(q.get("name"));
            if (DLQ_ROUTES.containsKey(name)) {
                long messages = ((Number) q.getOrDefault("messages", 0)).longValue();
                result.add(new DlqQueueStatus(name, messages));
            }
        }
        return result;
    }

    @Override
    public List<DlqMessageView> messages(String queue, int count) {
        requireKnownQueue(queue);
        int n = Math.max(1, Math.min(count, 100));
        // ack_requeue_true：取出后重新入队，仅预览不消费
        List<Map<String, Object>> raw = auth(restClient.post()
                .uri(mgmtBaseUrl + "/api/queues/%2F/" + queue + "/get")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("count", n, "ackmode", "ack_requeue_true", "encoding", "auto", "truncate", 50000)))
                .retrieve().body(MAP_LIST);
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(m -> new DlqMessageView(
                        String.valueOf(m.get("payload")),
                        String.valueOf(m.get("routing_key")),
                        Boolean.TRUE.equals(m.get("redelivered"))))
                .toList();
    }

    @Override
    @Audited(action = "DLQ_REQUEUE", objectType = "dlq")
    public int requeue(String queue) {
        requireKnownQueue(queue);
        String routingKey = DLQ_ROUTES.get(queue);
        List<Map<String, Object>> dead = auth(restClient.post()
                .uri(mgmtBaseUrl + "/api/queues/%2F/" + queue + "/get")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("count", REQUEUE_BATCH_MAX, "ackmode", "ack_requeue_false", "encoding", "auto", "truncate", 50000)))
                .retrieve().body(MAP_LIST);
        if (dead == null || dead.isEmpty()) {
            return 0;
        }
        MessagePostProcessor contentTypeJson = msg -> {
            msg.getMessageProperties().setContentType("application/json");
            return msg;
        };
        for (Map<String, Object> m : dead) {
            String payload = String.valueOf(m.get("payload"));
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS, routingKey, payload, contentTypeJson);
        }
        return dead.size();
    }

    @Override
    @Audited(action = "DLQ_PURGE", objectType = "dlq")
    public long purge(String queue) {
        requireKnownQueue(queue);
        long count = queues().stream()
                .filter(s -> s.queue().equals(queue))
                .mapToLong(DlqQueueStatus::messages)
                .findFirst().orElse(0);
        auth(restClient.delete()
                .uri(mgmtBaseUrl + "/api/queues/%2F/" + queue + "/contents"))
                .retrieve().toBodilessEntity();
        return count;
    }

    // ---------- 内部方法 ----------

    private void requireKnownQueue(String queue) {
        if (!DLQ_ROUTES.containsKey(queue)) {
            throw new IllegalArgumentException("未知死信队列: " + queue + "（允许: " + DLQ_ROUTES.keySet() + "）");
        }
    }

    /** 统一附加管理台 Basic Auth */
    private RestClient.RequestHeadersSpec<?> auth(RestClient.RequestHeadersSpec<?> spec) {
        if (mgmtPassword == null || mgmtPassword.isBlank()) {
            throw new IllegalStateException("RabbitMQ 管理台密码未配置（ums.mgmt.password）");
        }
        return spec.headers(h -> h.setBasicAuth(mgmtUser, mgmtPassword));
    }
}
