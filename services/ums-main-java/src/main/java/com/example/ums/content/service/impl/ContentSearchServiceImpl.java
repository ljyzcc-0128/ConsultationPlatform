package com.example.ums.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.content.dto.NewsListItemDto;
import com.example.ums.content.dto.NewsPageDto;
import com.example.ums.content.dto.RelatedContentDto;
import com.example.ums.content.dto.TimelineEventDto;
import com.example.ums.content.service.ContentSearchService;
import com.example.ums.content.util.CursorCodec;
import com.example.ums.feed.mapper.ChangeEventMapper;
import com.example.ums.feed.model.ChangeEvent;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内容搜索与关联服务实现。
 */
@Service
public class ContentSearchServiceImpl implements ContentSearchService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_RELATED = 5;

    private final NewsMapper newsMapper;
    private final ChangeEventMapper changeEventMapper;
    private final ObjectMapper objectMapper;

    public ContentSearchServiceImpl(NewsMapper newsMapper,
                                    ChangeEventMapper changeEventMapper,
                                    ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.changeEventMapper = changeEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public NewsPageDto search(String q, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        LambdaQueryWrapper<CpNews> wrapper = new LambdaQueryWrapper<CpNews>()
                .eq(CpNews::getManualReviewStatus, "Approved")
                .orderByDesc(CpNews::getPublishDate)
                .orderByDesc(CpNews::getArticleId);

        if (q != null && !q.isBlank()) {
            wrapper.apply("MATCH(title, summary) AGAINST({0} IN BOOLEAN MODE)", q.trim());
        }
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor c = CursorCodec.decode(cursor);
            wrapper.and(w -> w.lt(CpNews::getPublishDate, c.publishDate())
                    .or(w2 -> w2.eq(CpNews::getPublishDate, c.publishDate())
                                .lt(CpNews::getArticleId, c.articleId())));
        }

        List<CpNews> rows = newsMapper.selectList(wrapper.last("LIMIT " + (pageSize + 1)));
        boolean hasMore = rows.size() > pageSize;
        List<CpNews> page = hasMore ? rows.subList(0, pageSize) : rows;

        List<NewsListItemDto> items = page.stream().map(this::toListItem).toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? CursorCodec.encode(page.get(page.size() - 1).getPublishDate(),
                                     page.get(page.size() - 1).getArticleId())
                : null;
        return new NewsPageDto(items, nextCursor, hasMore);
    }

    @Override
    public List<RelatedContentDto> findRelated(String articleId) {
        CpNews source = newsMapper.selectById(articleId);
        if (source == null) {
            return List.of();
        }

        List<RelatedContentDto> result = new ArrayList<>();
        List<String> excludeIds = new ArrayList<>(List.of(articleId));

        // 1. 同 duplicate_group_id 查同组文章（HIGH 相关度）
        if (source.getDuplicateGroupId() != null && !source.getDuplicateGroupId().isBlank()) {
            LambdaQueryWrapper<CpNews> dupWrapper = new LambdaQueryWrapper<CpNews>()
                    .eq(CpNews::getDuplicateGroupId, source.getDuplicateGroupId())
                    .ne(CpNews::getArticleId, articleId)
                    .eq(CpNews::getManualReviewStatus, "Approved")
                    .orderByDesc(CpNews::getPublishDate)
                    .last("LIMIT " + MAX_RELATED);
            List<CpNews> dupNews = newsMapper.selectList(dupWrapper);
            for (CpNews n : dupNews) {
                result.add(new RelatedContentDto(n.getArticleId(), n.getTitle(),
                        n.getSourceName(), n.getPublishDate(), "HIGH"));
                excludeIds.add(n.getArticleId());
            }
        }

        // 2. 不足 MAX_RELATED 条时按同 category 补充（NORMAL 相关度）
        if (result.size() < MAX_RELATED && source.getCategory() != null) {
            LambdaQueryWrapper<CpNews> catWrapper = new LambdaQueryWrapper<CpNews>()
                    .eq(CpNews::getManualReviewStatus, "Approved")
                    .ne(CpNews::getArticleId, articleId)
                    .notIn(!excludeIds.isEmpty(), CpNews::getArticleId, excludeIds)
                    .apply("JSON_CONTAINS(category, {0})", "\"" + parseFirstCategory(source.getCategory()) + "\"")
                    .orderByDesc(CpNews::getPublishDate)
                    .last("LIMIT " + (MAX_RELATED - result.size()));
            List<CpNews> catNews = newsMapper.selectList(catWrapper);
            for (CpNews n : catNews) {
                result.add(new RelatedContentDto(n.getArticleId(), n.getTitle(),
                        n.getSourceName(), n.getPublishDate(), "NORMAL"));
            }
        }

        return result;
    }

    @Override
    public List<TimelineEventDto> findTimeline(String articleId) {
        LambdaQueryWrapper<ChangeEvent> wrapper = new LambdaQueryWrapper<ChangeEvent>()
                .eq(ChangeEvent::getArticleId, articleId)
                .orderByAsc(ChangeEvent::getDetectedAt);
        List<ChangeEvent> events = changeEventMapper.selectList(wrapper);
        return events.stream()
                .map(e -> new TimelineEventDto(e.getDetectedAt(), e.getEventType(), e.getDescription()))
                .toList();
    }

    private NewsListItemDto toListItem(CpNews n) {
        return new NewsListItemDto(n.getArticleId(), n.getSourceId(), n.getSourceName(),
                n.getTitle(), n.getOriginalUrl(), n.getPublishDate(), n.getPublishTime(),
                n.getAuthor(), n.getLanguage(), parseCategory(n.getCategory()),
                n.getSummary(), n.getDuplicateGroupId(), n.getManualReviewStatus());
    }

    /** category JSON 文本（["BESS","Policy"]）→ List；空或格式错返回 null */
    private List<String> parseCategory(String categoryJson) {
        if (categoryJson == null || categoryJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(categoryJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 category JSON 数组中第一个元素（用于同分类查询） */
    private String parseFirstCategory(String categoryJson) {
        List<String> cats = parseCategory(categoryJson);
        return (cats != null && !cats.isEmpty()) ? cats.get(0) : "";
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
