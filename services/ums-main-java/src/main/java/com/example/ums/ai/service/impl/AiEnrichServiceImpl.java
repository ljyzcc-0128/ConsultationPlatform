package com.example.ums.ai.service.impl;

import com.example.ums.ai.AIProcessingService;
import com.example.ums.ai.dto.ClassificationResult;
import com.example.ums.ai.mapper.AiLogMapper;
import com.example.ums.ai.model.AiProcessingLog;
import com.example.ums.ai.service.AiEnrichService;
import com.example.ums.common.util.UrlHashCalculator;
import com.example.ums.config.RabbitMqConfig;
import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.CpNewsContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 加工编排实现（实现指南 2.1 步骤 4）。
 *
 * 失败语义（AI-004：AI 故障不阻断采集——采集入库已前置完成，本层失败不影响数据落库）：
 * - AI 未配置 / 正文为空 → 记 SKIPPED 日志，消息 ack 放行
 * - summarize 失败 → 抛异常（消费重试 3 次 → DLQ，可重放）
 * - classify 失败 → 记 FAILED 日志，降级继续（分类非关键，可后续补）
 */
@Service
public class AiEnrichServiceImpl implements AiEnrichService {

    private static final Logger log = LoggerFactory.getLogger(AiEnrichServiceImpl.class);
    public static final String ROUTING_CONTENT_PROCESSED = "content.processed";

    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;
    private final AiLogMapper aiLogMapper;
    private final AIProcessingService aiService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiEnrichServiceImpl(NewsMapper newsMapper,
                               NewsContentMapper newsContentMapper,
                               AiLogMapper aiLogMapper,
                               AIProcessingService aiService,
                               RabbitTemplate rabbitTemplate,
                               ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.newsContentMapper = newsContentMapper;
        this.aiLogMapper = aiLogMapper;
        this.aiService = aiService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enrich(String articleId) {
        CpNews news = newsMapper.selectById(articleId);
        if (news == null) {
            throw new IllegalArgumentException("资讯不存在: " + articleId);
        }
        CpNewsContent content = newsContentMapper.selectById(articleId);
        String body = content != null ? content.getBody() : null;

        if (!aiService.isConfigured()) {
            record(articleId, "summarize", "SKIPPED", null, null, "AI 未配置（UMS_AI_API_KEY 为空）");
            log.info("AI 未配置，跳过加工 articleId={}", articleId);
            return;
        }
        if (body == null || body.isBlank()) {
            record(articleId, "summarize", "SKIPPED", null, null, "正文为空");
            log.info("正文为空，跳过加工 articleId={}", articleId);
            return;
        }

        String input = news.getTitle() + "\n\n" + body;
        String inputDigest = UrlHashCalculator.sha256Hex(input);

        // 1. 摘要（核心加工，失败抛异常走重试）
        long t0 = System.currentTimeMillis();
        String summary;
        try {
            summary = aiService.summarize(input, "zh-CN");
        } catch (Exception e) {
            record(articleId, "summarize", "FAILED",
                    (int) (System.currentTimeMillis() - t0), inputDigest, e.getMessage());
            throw e;
        }
        record(articleId, "summarize", "SUCCESS",
                (int) (System.currentTimeMillis() - t0), inputDigest, null);

        // 2. 分类（非关键，失败降级继续）
        long t1 = System.currentTimeMillis();
        List<String> categories = List.of();
        boolean classifyOk = false;
        try {
            ClassificationResult result = aiService.classify(input);
            categories = result.categories();
            classifyOk = !categories.isEmpty();
            record(articleId, "classify", "SUCCESS",
                    (int) (System.currentTimeMillis() - t1), inputDigest, null);
        } catch (Exception e) {
            record(articleId, "classify", "FAILED",
                    (int) (System.currentTimeMillis() - t1), inputDigest, e.getMessage());
            log.warn("分类失败，降级继续 articleId={}", articleId, e);
        }

        // 3. 回写（updateById NOT_NULL 策略：只更新 summary/category，不动采集字段）
        CpNews update = new CpNews();
        update.setArticleId(articleId);
        update.setSummary(summary);
        if (classifyOk) {
            update.setCategory(toJson(categories));
        }
        newsMapper.updateById(update);

        // 4. 发 content.processed（去重聚合链路入口）
        publish(articleId, classifyOk);
        log.info("AI 加工完成 articleId={} provider={} model={} classify={}",
                articleId, aiService.providerName(), aiService.modelName(), classifyOk);
    }

    private void record(String newsId, String operation, String status,
                        Integer latencyMs, String inputDigest, String error) {
        AiProcessingLog entry = new AiProcessingLog();
        entry.setNewsId(newsId);
        entry.setOperation(operation);
        entry.setProvider(aiService.providerName());
        entry.setModel(aiService.modelName());
        entry.setStatus(status);
        entry.setLatencyMs(latencyMs);
        entry.setInputDigest(inputDigest);
        if (error != null && error.length() > 500) {
            error = error.substring(0, 500);
        }
        entry.setErrorSummary(error);
        aiLogMapper.insert(entry);
    }

    private String toJson(List<String> categories) {
        try {
            return objectMapper.writeValueAsString(categories);
        } catch (Exception e) {
            return null;
        }
    }

    private void publish(String articleId, boolean classifyOk) {
        try {
            Map<String, Object> evt = classifyOk
                    ? Map.of("event", "content.processed", "contentId", articleId,
                             "aiGeneratedFields", List.of("summary", "category"),
                             "aiStatus", "SUCCESS")
                    : Map.of("event", "content.processed", "contentId", articleId,
                             "aiGeneratedFields", List.of("summary"),
                             "aiStatus", "SUCCESS");
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS, ROUTING_CONTENT_PROCESSED,
                    objectMapper.writeValueAsString(evt));
        } catch (Exception e) {
            log.warn("content.processed 发布失败 articleId={}", articleId, e);
        }
    }
}
