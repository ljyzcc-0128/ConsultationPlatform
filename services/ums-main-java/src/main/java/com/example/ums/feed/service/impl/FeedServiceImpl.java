package com.example.ums.feed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.content.dto.NewsListItemDto;
import com.example.ums.content.service.NewsQueryService;
import com.example.ums.feed.dto.FeedItemDto;
import com.example.ums.feed.mapper.ChangeEventMapper;
import com.example.ums.feed.mapper.ReadStatusMapper;
import com.example.ums.feed.model.ChangeEvent;
import com.example.ums.feed.service.FeedService;
import com.example.ums.push.dto.PushRecordDto;
import com.example.ums.push.service.PushMessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feed 服务实现：复用 NewsQueryService 取新闻 + change_events 重大变化 + 已读状态装配。
 */
@Service
public class FeedServiceImpl implements FeedService {

    private final NewsQueryService newsQueryService;
    private final ChangeEventMapper changeEventMapper;
    private final ReadStatusMapper readStatusMapper;
    private final PushMessageService pushMessageService;

    public FeedServiceImpl(NewsQueryService newsQueryService,
                           ChangeEventMapper changeEventMapper,
                           ReadStatusMapper readStatusMapper,
                           PushMessageService pushMessageService) {
        this.newsQueryService = newsQueryService;
        this.changeEventMapper = changeEventMapper;
        this.readStatusMapper = readStatusMapper;
        this.pushMessageService = pushMessageService;
    }

    @Override
    public List<FeedItemDto> listFeed(String userId, String tab) {
        // 1. 取已审核资讯（第一页 20 条）
        List<NewsListItemDto> allNews = newsQueryService
                .listTimeline(null, 20, null, null, null, null, null, "Approved")
                .items();

        // 2. 取重大变化事件关联的 articleId 集合（按检测时间倒序）
        List<ChangeEvent> majorEvents = changeEventMapper.selectList(
                new LambdaQueryWrapper<ChangeEvent>()
                        .eq(ChangeEvent::getEventType, "MAJOR_CHANGE")
                        .orderByDesc(ChangeEvent::getDetectedAt));
        Set<String> majorArticleIds = majorEvents.stream()
                .map(ChangeEvent::getArticleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 按 tab 过滤
        List<NewsListItemDto> filtered;
        switch (tab == null ? "" : tab) {
            case "today":
                LocalDate today = LocalDate.now();
                filtered = allNews.stream()
                        .filter(n -> today.equals(n.publishDate()))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    filtered = allNews.stream().limit(4).collect(Collectors.toList());
                }
                break;
            case "major_changes":
                filtered = allNews.stream()
                        .filter(n -> majorArticleIds.contains(n.articleId())
                                || (n.category() != null && n.category().contains("Policy")))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    filtered = allNews;
                }
                break;
            case "updates":
            default:
                filtered = allNews;
                break;
        }

        // 4. 批量查询已读状态
        List<String> articleIds = filtered.stream()
                .map(NewsListItemDto::articleId)
                .collect(Collectors.toList());
        Set<String> readSet = articleIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(readStatusMapper.findReadArticleIds(userId, articleIds));

        // 5. 装配 FeedItemDto
        return filtered.stream()
                .map(n -> toFeedItem(n, readSet, majorArticleIds))
                .collect(Collectors.toList());
    }

    @Override
    public List<PushRecordDto> listPushes(String userId) {
        return pushMessageService.listByUser(userId);
    }

    private FeedItemDto toFeedItem(NewsListItemDto n, Set<String> readSet, Set<String> majorArticleIds) {
        // 枚举代码统一使用前端 *_TEXT 映射约定的英文大写值
        String changeType = majorArticleIds.contains(n.articleId()) ? "MAJOR" : "NORMAL";
        String importance = (n.category() != null && n.category().contains("Policy")) ? "HIGH" : "NORMAL";
        List<String> aiGeneratedFields = n.summary() != null && !n.summary().isBlank()
                ? List.of("summary")
                : List.of();
        return new FeedItemDto(
                n.articleId(),
                n.title(),
                n.summary(),
                n.sourceName(),
                n.publishDate() != null ? n.publishDate().toString() : null,
                n.publishTime() != null ? n.publishTime().toString() : null,
                n.language(),
                n.category(),
                readSet.contains(n.articleId()),
                changeType,
                importance,
                aiGeneratedFields);
    }
}
