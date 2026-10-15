package com.example.ums.processing.service.impl;

import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.mapper.SourceMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.RawItemFetchedMessage;
import com.example.ums.processing.model.SourceBrief;
import com.example.ums.processing.service.NewsIngestService;
import com.example.ums.common.util.UrlHashCalculator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 入库实现：cp_news + cp_news_content 同一事务写入。
 *
 * 幂等设计（实现指南 2.4）：不依赖 MQ 的 at-least-once 语义，
 * 靠数据库唯一约束 uk_source_urlhash 兜底，重抓/重复消费均走更新。
 */
@Service
public class NewsIngestServiceImpl implements NewsIngestService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestServiceImpl.class);
    private static final String SUPPORTED_SCHEMA = "1.0";

    private final SourceMapper sourceMapper;
    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;

    public NewsIngestServiceImpl(SourceMapper sourceMapper,
                                 NewsMapper newsMapper,
                                 NewsContentMapper newsContentMapper) {
        this.sourceMapper = sourceMapper;
        this.newsMapper = newsMapper;
        this.newsContentMapper = newsContentMapper;
    }

    @Override
    @Transactional
    public String ingest(RawItemFetchedMessage message) {
        RawItemFetchedMessage.Item item = requireItem(message);

        if (!SUPPORTED_SCHEMA.equals(message.schema_version())) {
            log.warn("非预期 schema_version={}（支持 {}），按兼容模式继续处理", message.schema_version(), SUPPORTED_SCHEMA);
        }

        // 信源必须已在 cp_source 注册（种子数据），否则视为异常消息
        SourceBrief source = sourceMapper.findById(item.source_code());
        if (source == null) {
            throw new IllegalArgumentException("未知信源 source_code=" + item.source_code());
        }

        LocalDateTime publishTime = parseUtc(item.publish_time());

        CpNews news = new CpNews();
        news.setSourceId(item.source_code());
        news.setSourceName(source.getSourceName());
        news.setTitle(item.title());
        news.setOriginalUrl(item.original_url());
        news.setUrlHash(UrlHashCalculator.sha256Hex(item.original_url()));
        // 发布时间缺失保留 null（待审），不用采集时间替代（数据质量规则）
        news.setPublishTime(publishTime);
        news.setPublishDate(publishTime != null ? publishTime.toLocalDate() : null);
        news.setCrawlDate(parseUtcOrNow(message.fetched_at()));
        news.setAuthor(item.author());
        news.setLanguage(item.language() != null ? item.language() : "zh-CN");
        news.setTaskId(message.task_id());

        String articleId = upsert(news);
        newsContentMapper.upsert(articleId, item.body());
        log.info("入库完成 articleId={} source={} urlHash={}", articleId, item.source_code(), news.getUrlHash());
        return articleId;
    }

    /**
     * 幂等 upsert（MyBatis-Plus）：按唯一键 (source_id, url_hash) 先查后写，
     * 并发撞唯一键时捕获 DuplicateKeyException 转更新（单消费者下仅作兜底）。
     */
    private String upsert(CpNews news) {
        CpNews existing = newsMapper.selectOne(new LambdaQueryWrapper<CpNews>()
                .select(CpNews::getArticleId)
                .eq(CpNews::getSourceId, news.getSourceId())
                .eq(CpNews::getUrlHash, news.getUrlHash()));
        if (existing == null) {
            news.setArticleId(UUID.randomUUID().toString());
            try {
                newsMapper.insert(news);
                return news.getArticleId();
            } catch (DuplicateKeyException e) {
                // 并发插入已被其他事务抢占，回查真实 article_id 走更新
                existing = newsMapper.selectOne(new LambdaQueryWrapper<CpNews>()
                        .select(CpNews::getArticleId)
                        .eq(CpNews::getSourceId, news.getSourceId())
                        .eq(CpNews::getUrlHash, news.getUrlHash()));
            }
        }
        news.setArticleId(existing.getArticleId());
        // updateById 默认 NOT_NULL 策略：publish_time/author 为 null 时跳过，保留旧值
        newsMapper.updateById(news);
        return existing.getArticleId();
    }

    private static RawItemFetchedMessage.Item requireItem(RawItemFetchedMessage message) {
        if (message == null || message.item() == null) {
            throw new IllegalArgumentException("消息缺少 item 节点");
        }
        RawItemFetchedMessage.Item item = message.item();
        if (isBlank(item.source_code()) || isBlank(item.title()) || isBlank(item.original_url())) {
            throw new IllegalArgumentException("消息缺少必要字段（source_code/title/original_url）");
        }
        return item;
    }

    /** UTC ISO 8601 → LocalDateTime（UTC 口径存储）；缺失返回 null */
    private static LocalDateTime parseUtc(String iso) {
        if (isBlank(iso)) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
        } catch (Exception e) {
            throw new IllegalArgumentException("时间格式非法: " + iso, e);
        }
    }

    private static LocalDateTime parseUtcOrNow(String iso) {
        LocalDateTime parsed = parseUtc(iso);
        return parsed != null ? parsed : LocalDateTime.now(ZoneOffset.UTC);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
