package com.example.ums.ai.service.impl;

import com.example.ums.ai.AIProcessingService;
import com.example.ums.ai.dto.AnalysisResult;
import com.example.ums.ai.dto.ClassificationResult;
import com.example.ums.ai.mapper.AiLogMapper;
import com.example.ums.ai.model.AiProcessingLog;
import com.example.ums.ai.service.AiEnrichService;
import com.example.ums.common.util.UrlHashCalculator;
import com.example.ums.config.RabbitMqConfig;
import com.example.ums.processing.mapper.NewsAnalysisMapper;
import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.CpNewsAnalysis;
import com.example.ums.processing.model.CpNewsContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 加工编排实现（实现指南 2.1 步骤 4）。
 *
 * 失败语义（AI-004：AI 故障不阻断采集——采集入库已前置完成，本层失败不影响数据落库）：
 * - AI 未配置 / 正文为空 → 记 SKIPPED 日志，消息 ack 放行
 * - summarize 失败 → 抛异常（消费重试 3 次 → DLQ，可重放）
 * - classify / analyze 失败 → 记 FAILED 日志，降级继续（分类与分析非关键，可后续补）
 *
 * 幂等（管理端补加工场景）：summary/category/analysis 已存在的步骤跳过，不重复消耗 tokens。
 */
@Service
@RequiredArgsConstructor
public class AiEnrichServiceImpl implements AiEnrichService {

    private static final Logger log = LoggerFactory.getLogger(AiEnrichServiceImpl.class);
    public static final String ROUTING_CONTENT_PROCESSED = "content.processed";

    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;
    private final NewsAnalysisMapper newsAnalysisMapper;
    private final AiLogMapper aiLogMapper;
    private final AIProcessingService aiService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

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

        // 已有 summary/category/analysis 的步骤跳过（回填场景幂等）
        boolean needSummary = news.getSummary() == null || news.getSummary().isBlank();
        boolean needCategory = news.getCategory() == null || news.getCategory().isBlank();
        CpNewsAnalysis existingAnalysis = newsAnalysisMapper.selectById(articleId);
        // 分析行存在但缺发布机构 → 补分析（ issuing_authority 为空视为早期版本未产出）
        boolean needAnalysis = existingAnalysis == null
                || existingAnalysis.getIssuingAuthority() == null;
        if (!needSummary && !needCategory && !needAnalysis) {
            log.info("AI 加工字段已齐全，跳过 articleId={}", articleId);
            return;
        }

        String input = news.getTitle() + "\n\n" + body;
        String inputDigest = UrlHashCalculator.sha256Hex(input);

        // 1. 摘要（核心加工，失败抛异常走重试；已有摘要不重复生成）
        String summary = news.getSummary();
        if (needSummary) {
            long t0 = System.currentTimeMillis();
            try {
                summary = aiService.summarize(input, "zh-CN");
            } catch (Exception e) {
                record(articleId, "summarize", "FAILED",
                        (int) (System.currentTimeMillis() - t0), inputDigest, e.getMessage());
                throw e;
            }
            record(articleId, "summarize", "SUCCESS",
                    (int) (System.currentTimeMillis() - t0), inputDigest, null);
        }

        // 2. 分类（非关键，失败降级继续；已有分类不重复生成）
        List<String> categories = List.of();
        boolean classifyOk = false;
        if (needCategory) {
            long t1 = System.currentTimeMillis();
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
        } else {
            classifyOk = true;
        }

        // 3. 回写（updateById NOT_NULL 策略：只更新 AI 字段，不动采集字段）
        if (needSummary) {
            CpNews update = new CpNews();
            update.setArticleId(articleId);
            update.setSummary(summary);
            if (classifyOk && !categories.isEmpty()) {
                update.setCategory(toJson(categories));
            }
            newsMapper.updateById(update);
        }

        // 4. 政策与影响分析（写 cp_news_analysis，失败降级不阻断）
        boolean analysisOk = false;
        if (needAnalysis) {
            long t2 = System.currentTimeMillis();
            try {
                AnalysisResult analysis = analyze(input);
                boolean isUpdate = existingAnalysis != null;
                CpNewsAnalysis row = isUpdate ? existingAnalysis : new CpNewsAnalysis();
                row.setNewsId(articleId);
                row.setPolicyStatus(analysis.policyStatus());
                row.setPolicyName(analysis.policyName());
                // 非政策/未提及发布机构写 '[]' 而非 NULL：NULL 表示"尚未分析"，'[]' 表示"已分析但无发布机构"（回填幂等判据）
                row.setIssuingAuthority(analysis.issuingAuthority() != null ? analysis.issuingAuthority() : "[]");
                if (analysis.effectiveDate() != null && analysis.effectiveDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
                    row.setEffectiveDate(LocalDate.parse(analysis.effectiveDate()));
                }
                row.setBessRelevance(analysis.bessRelevance());
                row.setJinkoEssRelevance(analysis.jinkoEssRelevance());
                row.setImportanceScore(analysis.importanceScore());
                row.setAiModel(aiService.modelName());
                LocalDateTime now = LocalDateTime.now();
                row.setUpdatedAt(now);
                if (isUpdate) {
                    newsAnalysisMapper.updateById(row);
                } else {
                    row.setCreatedAt(now);
                    newsAnalysisMapper.insert(row);
                }
                analysisOk = true;
                record(articleId, "analyze", "SUCCESS",
                        (int) (System.currentTimeMillis() - t2), inputDigest, null);
            } catch (Exception e) {
                record(articleId, "analyze", "FAILED",
                        (int) (System.currentTimeMillis() - t2), inputDigest, e.getMessage());
                log.warn("政策分析失败，降级继续 articleId={}", articleId, e);
            }
        }

        // 5. 发 content.processed（去重聚合链路入口）
        List<String> generated = new ArrayList<>();
        if (needSummary) {
            generated.add("summary");
        }
        if (needSummary && classifyOk && !categories.isEmpty()) {
            generated.add("category");
        }
        if (analysisOk) {
            generated.add("analysis");
        }
        publish(articleId, generated);
        log.info("AI 加工完成 articleId={} provider={} model={} fields={}",
                articleId, aiService.providerName(), aiService.modelName(), generated);
    }

    /**
     * 政策与影响分析：JSON 结构化输出，解析容错（截取首个 { 到末个 } 之间的内容）。
     */
    private AnalysisResult analyze(String input) throws Exception {
        String systemPrompt = """
                你是储能行业资讯分析助手。基于文章标题与正文输出 JSON 分析结果，只输出 JSON，不要解释。
                字段定义：
                - policyStatus: 政策状态，枚举 DRAFT(征求意见/草案) / PUBLISHED(已发布) / IN_FORCE(已生效) / AMENDED(修订) / NON_POLICY(非政策类资讯)
                - policyName: 政策或文件全称，非政策类或原文未提及时为 null
                - issuingAuthority: 发布机构全称数组（如 ["国家能源局","国家发改委"]），非政策类或原文未明确提及为 null，禁止推断
                - effectiveDate: 生效日期 yyyy-MM-dd，原文未明确提及为 null，禁止推断
                - bessRelevance: 与储能(BESS)行业相关性 HIGH / MEDIUM / LOW
                - jinkoEssRelevance: 与晶科能源储能业务(Jinko ESS)相关性 HIGH / MEDIUM / LOW
                - importanceScore: 行业重要性 1-10 整数
                """;
        String raw = aiService.complete(systemPrompt, truncateForAnalyze(input));
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 分析返回非 JSON: " + raw.substring(0, Math.min(raw.length(), 120)));
        }
        JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
        Integer score = node.path("importanceScore").isNumber()
                ? node.path("importanceScore").asInt() : null;
        String effective = node.path("effectiveDate").isTextual() ? node.path("effectiveDate").asText() : null;
        String authority = null;
        JsonNode authNode = node.path("issuingAuthority");
        if (authNode.isArray() && authNode.size() > 0) {
            List<String> list = new ArrayList<>();
            authNode.forEach(n -> {
                String s = n.isTextual() ? n.asText().trim() : null;
                if (s != null && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
                    list.add(s);
                }
            });
            if (!list.isEmpty()) {
                authority = objectMapper.writeValueAsString(list);
            }
        }
        return new AnalysisResult(
                textOrNull(node, "policyStatus"),
                textOrNull(node, "policyName"),
                authority,
                effective,
                textOrNull(node, "bessRelevance"),
                textOrNull(node, "jinkoEssRelevance"),
                score == null || score < 1 || score > 10 ? null : score);
    }

    private String truncateForAnalyze(String text) {
        return text.length() > 6000 ? text.substring(0, 6000) : text;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        String s = v.isTextual() ? v.asText().trim() : (!v.isNull() && v.isValueNode() ? v.asText().trim() : null);
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
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

    private void publish(String articleId, List<String> aiGeneratedFields) {
        try {
            Map<String, Object> evt = Map.of("event", "content.processed", "contentId", articleId,
                    "aiGeneratedFields", aiGeneratedFields,
                    "aiStatus", "SUCCESS");
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS, ROUTING_CONTENT_PROCESSED,
                    objectMapper.writeValueAsString(evt));
        } catch (Exception e) {
            log.warn("content.processed 发布失败 articleId={}", articleId, e);
        }
    }
}
