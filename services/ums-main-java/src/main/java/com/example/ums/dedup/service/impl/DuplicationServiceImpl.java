package com.example.ums.dedup.service.impl;

import com.example.ums.common.util.TextNormalizer;
import com.example.ums.common.util.UrlHashCalculator;
import com.example.ums.config.RabbitMqConfig;
import com.example.ums.dedup.service.DuplicationService;
import com.example.ums.dedup.util.SimilarityMatcher;
import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.CpNewsContent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 去重聚实现：指纹精确匹配 / 标题归一化相等 / 标题 3-gram Jaccard ≥ 0.75（且日期差 ≤ 7 天）。
 *
 * - 仅跨源比对（同源重复已被 UNIQUE(source_id, url_hash) 挡住，不会产生两条记录）
 * - 一期数据量小（全量内存比对）；数据量大后可改为指纹索引预筛 + 候选集相似度计算
 * - 单消费者串行处理，无并发分裂组的场景
 */
@Service
public class DuplicationServiceImpl implements DuplicationService {

    private static final Logger log = LoggerFactory.getLogger(DuplicationServiceImpl.class);
    public static final String ROUTING_CONTENT_FINALIZED = "content.finalized";

    /** 指纹输入的正文截断长度（防超长正文；指纹比对场景 2000 归一化字符足够） */
    private static final int FINGERPRINT_BODY_LIMIT = 2000;

    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public DuplicationServiceImpl(NewsMapper newsMapper,
                                  NewsContentMapper newsContentMapper,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.newsContentMapper = newsContentMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void aggregate(String articleId) {
        CpNews news = newsMapper.selectById(articleId);
        if (news == null) {
            throw new IllegalArgumentException("资讯不存在: " + articleId);
        }
        CpNewsContent content = newsContentMapper.selectById(articleId);
        String body = content != null ? content.getBody() : "";

        // 1. 计算并回填指纹（归一化标题 + 归一化正文截断）
        String normTitle = TextNormalizer.normalize(news.getTitle());
        String normBody = truncate(TextNormalizer.normalize(body), FINGERPRINT_BODY_LIMIT);
        String fingerprint = UrlHashCalculator.sha256Hex(normTitle + "\n" + normBody);
        news.setContentFingerprint(fingerprint);  // 同步内存对象，供下方精确匹配
        CpNews fpUpdate = new CpNews();
        fpUpdate.setArticleId(articleId);
        fpUpdate.setContentFingerprint(fingerprint);
        newsMapper.updateById(fpUpdate);

        // 2. 跨源匹配
        List<CpNews> others = newsMapper.selectList(new LambdaQueryWrapper<CpNews>()
                .ne(CpNews::getArticleId, articleId)
                .ne(CpNews::getSourceId, news.getSourceId()));
        List<CpNews> matches = new ArrayList<>();
        for (CpNews other : others) {
            if (matches(news, other, normTitle)) {
                matches.add(other);
            }
        }

        // 3. 归组
        String groupId = null;
        if (!matches.isEmpty()) {
            groupId = matches.stream()
                    .map(CpNews::getDuplicateGroupId)
                    .filter(g -> g != null && !g.isBlank())
                    .findFirst()
                    .orElse("dg-" + UUID.randomUUID().toString().replace("-", ""));
            List<String> toGroup = new ArrayList<>();
            toGroup.add(articleId);
            for (CpNews m : matches) {
                if (m.getDuplicateGroupId() == null || m.getDuplicateGroupId().isBlank()) {
                    toGroup.add(m.getArticleId());
                }
            }
            newsMapper.update(null, new LambdaUpdateWrapper<CpNews>()
                    .set(CpNews::getDuplicateGroupId, groupId)
                    .in(CpNews::getArticleId, toGroup));
            log.info("去重聚归组 groupId={} members={}（当前+匹配 {}）",
                    groupId, toGroup.size(), matches.size());
        } else {
            log.info("无跨源重复，未归组 articleId={}", articleId);
        }

        // 4. 发 content.finalized（索引/推送链路入口）
        publish(articleId, groupId);
    }

    /** 匹配规则：指纹精确（需对方已有指纹）→ 标题归一化相等 → 标题相似且日期差达标 */
    private boolean matches(CpNews current, CpNews other, String currentNormTitle) {
        if (other.getContentFingerprint() != null
                && other.getContentFingerprint().equals(current.getContentFingerprint())) {
            return true;
        }
        String otherNormTitle = TextNormalizer.normalize(other.getTitle());
        if (!otherNormTitle.isEmpty() && otherNormTitle.equals(currentNormTitle)) {
            return true;
        }
        if (SimilarityMatcher.similarTitle(current.getTitle(), other.getTitle())) {
            return withinDateGap(current, other);
        }
        return false;
    }

    private boolean withinDateGap(CpNews a, CpNews b) {
        if (a.getPublishDate() == null || b.getPublishDate() == null) {
            // 发布日期缺失：不因日期否决（数据文档：日期缺失保留待审，不替代）
            return true;
        }
        long gap = Math.abs(ChronoUnit.DAYS.between(a.getPublishDate(), b.getPublishDate()));
        return gap <= SimilarityMatcher.MAX_DATE_GAP_DAYS;
    }

    private String truncate(String s, int limit) {
        return s != null && s.length() > limit ? s.substring(0, limit) : s;
    }

    private void publish(String articleId, String groupId) {
        try {
            Map<String, Object> evt = groupId != null
                    ? Map.of("event", "content.finalized", "contentId", articleId,
                             "duplicateGroupId", groupId)
                    : Map.of("event", "content.finalized", "contentId", articleId);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_UMS, ROUTING_CONTENT_FINALIZED,
                    objectMapper.writeValueAsString(evt));
        } catch (Exception e) {
            log.warn("content.finalized 发布失败 articleId={}", articleId, e);
        }
    }
}
