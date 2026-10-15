package com.example.ums.processing.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 消息契约测试：Java DTO 必须能解析 ums-crawler 实际发布的 schema 1.0 消息
 * （含 publish_time 为 null 的场景——数据质量规则：缺失保留，不用抓取时间替代）。
 */
class RawItemFetchedMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 解析完整消息() throws Exception {
        String payload = """
                {
                  "schema_version": "1.0",
                  "event": "raw.item.fetched",
                  "task_id": "CN-01-20260918-abc123",
                  "fetched_at": "2026-09-18T06:00:00.123456Z",
                  "item": {
                    "source_code": "CN-01",
                    "title": "某储能项目并网",
                    "body": "正文第一段……",
                    "author": null,
                    "publish_time": "2026-09-17T02:30:00Z",
                    "original_url": "https://www.escn.com.cn/news/2026/0917/123456.html",
                    "language": "zh-CN"
                  }
                }
                """;
        RawItemFetchedMessage message = objectMapper.readValue(payload, RawItemFetchedMessage.class);

        assertEquals("1.0", message.schema_version());
        assertEquals("raw.item.fetched", message.event());
        assertEquals("CN-01-20260918-abc123", message.task_id());
        assertEquals("2026-09-18T06:00:00.123456Z", message.fetched_at());
        assertEquals("CN-01", message.item().source_code());
        assertEquals("某储能项目并网", message.item().title());
        assertNull(message.item().author());
        assertEquals("2026-09-17T02:30:00Z", message.item().publish_time());
        assertEquals("https://www.escn.com.cn/news/2026/0917/123456.html", message.item().original_url());
        assertEquals("zh-CN", message.item().language());
    }

    @Test
    void 解析publish_time为null的消息() throws Exception {
        String payload = """
                {
                  "schema_version": "1.0",
                  "event": "raw.item.fetched",
                  "task_id": "t-1",
                  "fetched_at": "2026-09-18T06:00:00Z",
                  "item": {
                    "source_code": "INT-02",
                    "title": "Battery storage news",
                    "body": "body text",
                    "author": "John Doe",
                    "publish_time": null,
                    "original_url": "https://www.energy-storage.news/news/foo",
                    "language": "en"
                  }
                }
                """;
        RawItemFetchedMessage message = objectMapper.readValue(payload, RawItemFetchedMessage.class);

        assertNull(message.item().publish_time());
        assertEquals("John Doe", message.item().author());
    }

    @Test
    void 解析含未知字段的消息_向前兼容() throws Exception {
        String payload = """
                {
                  "schema_version": "1.1",
                  "event": "raw.item.fetched",
                  "task_id": "t-2",
                  "fetched_at": "2026-09-18T06:00:00Z",
                  "extra_field": "未来版本新增字段",
                  "item": {
                    "source_code": "CN-02",
                    "title": "标题",
                    "body": "正文",
                    "author": null,
                    "publish_time": null,
                    "original_url": "https://example.org/a",
                    "language": "zh-CN",
                    "another_extra": 1
                  }
                }
                """;
        RawItemFetchedMessage message = objectMapper.readValue(payload, RawItemFetchedMessage.class);
        assertEquals("CN-02", message.item().source_code());
    }
}
