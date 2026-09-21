package com.example.ums.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.content.dto.NewsDetailDto;
import com.example.ums.content.dto.NewsListItemDto;
import com.example.ums.content.dto.NewsPageDto;
import com.example.ums.content.service.NewsQueryService;
import com.example.ums.content.util.CursorCodec;
import com.example.ums.processing.mapper.NewsAnalysisMapper;
import com.example.ums.processing.mapper.NewsContentMapper;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.example.ums.processing.model.CpNewsAnalysis;
import com.example.ums.processing.model.CpNewsContent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class NewsQueryServiceImpl implements NewsQueryService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;

    private final NewsMapper newsMapper;
    private final NewsContentMapper newsContentMapper;
    private final NewsAnalysisMapper newsAnalysisMapper;
    private final ObjectMapper objectMapper;

    public NewsQueryServiceImpl(NewsMapper newsMapper,
                                NewsContentMapper newsContentMapper,
                                NewsAnalysisMapper newsAnalysisMapper,
                                ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.newsContentMapper = newsContentMapper;
        this.newsAnalysisMapper = newsAnalysisMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public NewsPageDto listTimeline(String cursor, int limit, String sourceId, String category,
                                    String language, LocalDate dateFrom, LocalDate dateTo,
                                    String reviewStatus) {
        int pageSize = normalizeLimit(limit);
        LambdaQueryWrapper<CpNews> wrapper = new LambdaQueryWrapper<CpNews>()
                .orderByDesc(CpNews::getPublishDate)   // NULL 默认排 DESC 末尾（待审数据）
                .orderByDesc(CpNews::getArticleId);    // 同日复合游标稳定排序
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor c = CursorCodec.decode(cursor);
            wrapper.and(w -> w.lt(CpNews::getPublishDate, c.publishDate())
                    .or(w2 -> w2.eq(CpNews::getPublishDate, c.publishDate())
                                .lt(CpNews::getArticleId, c.articleId())));
        }
        if (sourceId != null && !sourceId.isBlank()) {
            wrapper.eq(CpNews::getSourceId, sourceId.trim());
        }
        if (category != null && !category.isBlank()) {
            // JSON 数组元素匹配：JSON_CONTAINS(category, '"BESS"')
            wrapper.apply("JSON_CONTAINS(category, {0})", "\"" + category.trim() + "\"");
        }
        if (language != null && !language.isBlank()) {
            wrapper.eq(CpNews::getLanguage, language.trim());
        }
        if (dateFrom != null) {
            wrapper.ge(CpNews::getPublishDate, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(CpNews::getPublishDate, dateTo);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            wrapper.eq(CpNews::getManualReviewStatus, reviewStatus.trim());
        }

        // limit+1 探测是否还有下一页
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
    public NewsDetailDto getDetail(String articleId) {
        CpNews news = newsMapper.selectById(articleId);
        if (news == null) {
            return null;
        }
        CpNewsContent content = newsContentMapper.selectById(articleId);
        CpNewsAnalysis analysis = newsAnalysisMapper.selectById(articleId);
        NewsListItemDto base = toListItem(news);
        NewsDetailDto.AnalysisDto analysisDto = analysis != null
                ? new NewsDetailDto.AnalysisDto(analysis.getPolicyStatus(), analysis.getPolicyName(),
                        analysis.getEffectiveDate(), analysis.getBessRelevance(),
                        analysis.getJinkoEssRelevance(), analysis.getImportanceScore())
                : null;
        return new NewsDetailDto(base.articleId(), base.sourceId(), base.sourceName(),
                base.title(), base.originalUrl(), base.publishDate(), base.publishTime(),
                base.author(), base.language(), base.category(), base.summary(),
                base.duplicateGroupId(), base.manualReviewStatus(),
                content != null ? content.getBody() : null, analysisDto);
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

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
