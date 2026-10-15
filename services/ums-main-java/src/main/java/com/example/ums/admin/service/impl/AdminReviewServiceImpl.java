package com.example.ums.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.annotation.Audited;
import com.example.ums.admin.dto.ReviewItemDto;
import com.example.ums.admin.service.AdminReviewService;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 审核队列实现（状态机：Pending/NeedsReview → Approved | Rejected）。 */
@Service
public class AdminReviewServiceImpl implements AdminReviewService {

    /** 允许执行审核动作的来源状态 */
    private static final Set<String> ACTIONABLE = Set.of("Pending", "NeedsReview");

    private final NewsMapper newsMapper;
    private final ObjectMapper objectMapper;

    public AdminReviewServiceImpl(NewsMapper newsMapper, ObjectMapper objectMapper) {
        this.newsMapper = newsMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ReviewItemDto> list(String status, int limit, int offset) {
        LambdaQueryWrapper<CpNews> wrapper = new LambdaQueryWrapper<CpNews>()
                .eq(status != null && !status.isBlank(), CpNews::getManualReviewStatus, status)
                .orderByDesc(CpNews::getUpdatedAt)
                .last("LIMIT " + clamp(limit, 1, 100) + " OFFSET " + Math.max(0, offset));
        return newsMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    @Override
    @Audited(action = "REVIEW_APPROVE", objectType = "cp_news")
    public void approve(String articleId, String comment) {
        CpNews news = requireNews(articleId);
        if ("Approved".equals(news.getManualReviewStatus())) {
            return; // 幂等：已通过的重复操作不报错
        }
        requireActionable(news);
        news.setManualReviewStatus("Approved");
        news.setReviewComment(comment);
        newsMapper.updateById(news);
    }

    @Override
    @Audited(action = "REVIEW_REJECT", objectType = "cp_news")
    public void reject(String articleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("驳回必须填写原因");
        }
        CpNews news = requireNews(articleId);
        if ("Rejected".equals(news.getManualReviewStatus())) {
            return; // 幂等
        }
        requireActionable(news);
        news.setManualReviewStatus("Rejected");
        news.setReviewComment(reason);
        newsMapper.updateById(news);
    }

    @Override
    @Audited(action = "REVIEW_CATEGORY_FIX", objectType = "cp_news")
    public void updateCategory(String articleId, List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("分类列表不能为空");
        }
        CpNews news = requireNews(articleId);
        try {
            news.setCategory(objectMapper.writeValueAsString(categories));
        } catch (Exception e) {
            throw new IllegalStateException("分类序列化失败", e);
        }
        newsMapper.updateById(news);
    }

    private CpNews requireNews(String articleId) {
        CpNews news = newsMapper.selectById(articleId);
        if (news == null) {
            throw new IllegalArgumentException("文章不存在: " + articleId);
        }
        return news;
    }

    private void requireActionable(CpNews news) {
        if (!ACTIONABLE.contains(news.getManualReviewStatus())) {
            throw new IllegalStateException("当前状态 " + news.getManualReviewStatus()
                    + " 不允许执行审核操作（仅 Pending/NeedsReview）");
        }
    }

    private ReviewItemDto toDto(CpNews news) {
        String summary = news.getSummary();
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }
        return new ReviewItemDto(
                news.getArticleId(),
                news.getTitle(),
                news.getSourceId(),
                news.getSourceName(),
                news.getPublishDate(),
                news.getLanguage(),
                news.getCategory(),
                summary,
                news.getManualReviewStatus(),
                news.getReviewComment(),
                news.getUpdatedAt());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
