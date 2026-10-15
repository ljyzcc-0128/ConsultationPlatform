package com.example.ums.ai.web;

import com.example.ums.ai.model.ContentParsedMessage;
import com.example.ums.config.RabbitMqConfig;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 补加工内部接口（与爬虫 /internal/trigger 同级，一期内网信任，不鉴权）。
 *
 * - POST /internal/ai/reprocess?articleId=xxx     单条重新加工
 * - POST /internal/ai/reprocess?pendingOnly=true  补加工所有未生成摘要的记录（上限 100 条/次）
 */
@RestController
@RequestMapping("/internal/ai")
public class AiInternalController {

    private static final int BATCH_LIMIT = 100;

    private final NewsMapper newsMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiInternalController(NewsMapper newsMapper,
                                RabbitTemplate rabbitTemplate,
                                ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Object>> reprocess(
            @RequestParam(required = false) String articleId,
            @RequestParam(defaultValue = "false") boolean pendingOnly) {

        List<String> targets;
        if (articleId != null && !articleId.isBlank()) {
            CpNews news = newsMapper.selectById(articleId);
            if (news == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "资讯不存在: " + articleId));
            }
            targets = List.of(articleId);
        } else if (pendingOnly) {
            // 补加工：summary 或 category 任一缺失（如分类失败已降级、或早期 SKIPPED 的记录）
            targets = newsMapper.selectObjs(new LambdaQueryWrapper<CpNews>()
                            .select(CpNews::getArticleId)
                            .isNull(CpNews::getSummary)
                            .or()
                            .isNull(CpNews::getCategory)
                            .last("LIMIT " + BATCH_LIMIT))
                    .stream().map(Object::toString).toList();
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "指定 articleId 或 pendingOnly=true"));
        }

        int published = 0;
        for (String id : targets) {
            if (publishContentParsed(id)) {
                published++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("targets", targets.size());
        result.put("published", published);
        return ResponseEntity.ok(result);
    }

    private boolean publishContentParsed(String articleId) {
        try {
            ContentParsedMessage evt = new ContentParsedMessage(
                    ContentParsedMessage.ROUTING_KEY, articleId, null);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS,
                    ContentParsedMessage.ROUTING_KEY, objectMapper.writeValueAsString(evt));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
