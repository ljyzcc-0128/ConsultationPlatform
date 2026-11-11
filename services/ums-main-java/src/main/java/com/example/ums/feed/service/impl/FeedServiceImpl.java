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
import com.example.ums.subscription.mapper.SubscriptionMapper;
import com.example.ums.subscription.model.CpSubscription;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feed 服务实现：按用户订阅规则过滤资讯 + change_events 重大变化 + 已读状态装配。
 */
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final NewsQueryService newsQueryService;
    private final ChangeEventMapper changeEventMapper;
    private final ReadStatusMapper readStatusMapper;
    private final PushMessageService pushMessageService;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    public List<FeedItemDto> listFeed(String userId, String tab) {
        // 1. 用户的启用订阅规则（关注什么就展示什么；无订阅则返回空）
        List<CpSubscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<CpSubscription>()
                        .eq(CpSubscription::getUserId, userId)
                        .eq(CpSubscription::getEnabled, true));
        if (subscriptions.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 取最新资讯（扩池 100 条，供订阅规则匹配）
        List<NewsListItemDto> allNews = newsQueryService
                .listTimeline(null, 100, null, null, null, null, null)
                .items();

        // 3. 取重大变化事件关联的 articleId 集合（按检测时间倒序）
        List<ChangeEvent> majorEvents = changeEventMapper.selectList(
                new LambdaQueryWrapper<ChangeEvent>()
                        .eq(ChangeEvent::getEventType, "MAJOR_CHANGE")
                        .orderByDesc(ChangeEvent::getDetectedAt));
        Set<String> majorArticleIds = majorEvents.stream()
                .map(ChangeEvent::getArticleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // 4. 按订阅规则过滤：任一规则命中即保留
        List<NewsListItemDto> matched = allNews.stream()
                .filter(n -> matchesSubscriptions(n, subscriptions, majorArticleIds))
                .collect(Collectors.toList());

        // 5. 按 tab 过滤
        List<NewsListItemDto> filtered;
        switch (tab == null ? "" : tab) {
            case "today":
                LocalDate today = LocalDate.now();
                filtered = matched.stream()
                        .filter(n -> today.equals(n.publishDate()))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    filtered = matched.stream().limit(4).collect(Collectors.toList());
                }
                break;
            case "major_changes":
                filtered = matched.stream()
                        .filter(n -> majorArticleIds.contains(n.articleId())
                                || (n.category() != null && n.category().contains("Policy")))
                        .collect(Collectors.toList());
                if (filtered.isEmpty()) {
                    filtered = matched;
                }
                break;
            case "updates":
            default:
                filtered = matched;
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

    /** 判断资讯是否命中任一订阅规则。 */
    private boolean matchesSubscriptions(NewsListItemDto n, List<CpSubscription> subscriptions,
                                         Set<String> majorArticleIds) {
        String haystack = ((n.title() == null ? "" : n.title()) + " "
                + (n.summary() == null ? "" : n.summary())).toLowerCase(Locale.ROOT);
        List<String> categories = n.category() == null ? List.of()
                : n.category().stream()
                        .filter(c -> c != null)
                        .map(c -> c.toLowerCase(Locale.ROOT))
                        .toList();
        for (CpSubscription s : subscriptions) {
            String value = s.getValue() == null ? "" : s.getValue().trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            boolean hit = switch (s.getSubscriptionType() == null ? "" : s.getSubscriptionType()) {
                case "THEME", "CONTENT_DOMAIN" -> categories.contains(value);
                case "EVENT" -> majorArticleIds.contains(n.articleId()) || haystack.contains(value);
                // COMPANY / KEYWORD / INDICATOR：标题+摘要文本包含匹配
                default -> haystack.contains(value);
            };
            if (hit) {
                return true;
            }
        }
        return false;
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
