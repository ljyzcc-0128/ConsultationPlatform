package com.example.ums.admin.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.ai.service.AiEnrichService;
import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.CpNewsContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 补加工管理接口。
 *
 * 背景：AI 未配置（UMS_AI_API_KEY 为空）期间入库的资讯会被记 SKIPPED，
 * summary / category / 政策分析字段为空。配置 key 后用本接口批量回填历史数据。
 * 补加工逐条调用 AiEnrichService（AI 故障不阻断：单条失败记录后继续）。
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Slf4j
public class AdminAiEnrichController {

    private static final int MAX_BATCH = 200;

    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;
    private final AiEnrichService aiEnrichService;

    /**
     * 批量补加工：扫描 summary 为空且有正文的资讯，逐条 AI 加工。
     * 示例：POST /api/v1/admin/ai/enrich-backfill?limit=100
     */
    @PostMapping("/enrich-backfill")
    public Map<String, Object> enrichBackfill(@RequestParam(defaultValue = "50") int limit) {
        int batchSize = Math.min(Math.max(limit, 1), MAX_BATCH);
        // 待补：缺摘要 或 缺分析行 或 分析行缺发布机构（幂等重跑不重复消耗 tokens）
        List<CpNews> pending = newsMapper.selectList(new LambdaQueryWrapper<CpNews>()
                .and(w -> w.isNull(CpNews::getSummary)
                        .or().notExists("SELECT 1 FROM cp_news_analysis a WHERE a.news_id = article_id")
                        .or().exists("SELECT 1 FROM cp_news_analysis a WHERE a.news_id = article_id AND a.issuing_authority IS NULL"))
                .orderByDesc(CpNews::getUpdatedAt)
                .last("LIMIT " + batchSize));

        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (CpNews news : pending) {
            try {
                CpNewsContent content = newsContentMapper.selectById(news.getArticleId());
                if (content == null || content.getBody() == null || content.getBody().isBlank()) {
                    skipped++;
                    continue;
                }
                aiEnrichService.enrich(news.getArticleId());
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("补加工失败 articleId={} err={}", news.getArticleId(), e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", pending.size());
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }
}
