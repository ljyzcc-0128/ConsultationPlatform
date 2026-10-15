package com.example.ums.feed.web;

import com.example.ums.feed.service.FeedService;
import com.example.ums.feed.service.ReadStatusService;
import com.example.ums.security.SecurityContextHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 我的情报 Feed 接口 + 已读状态接口。
 */
@RestController
@RequestMapping("/api/me")
public class FeedController {

    private final FeedService feedService;
    private final ReadStatusService readStatusService;

    public FeedController(FeedService feedService, ReadStatusService readStatusService) {
        this.feedService = feedService;
        this.readStatusService = readStatusService;
    }

    /**
     * Feed 列表或推送记录。
     *
     * @param tab today / major_changes / updates / pushes
     */
    @GetMapping("/feed")
    public Object feed(@RequestParam(defaultValue = "today") String tab) {
        String userId = SecurityContextHelper.currentUserId();
        if ("pushes".equals(tab)) {
            return feedService.listPushes(userId);
        }
        return feedService.listFeed(userId, tab);
    }

    /** 批量标记已读。 */
    @PostMapping("/read-status/batch")
    public Map<String, Object> batchMarkRead(@RequestBody Map<String, List<String>> body) {
        String userId = SecurityContextHelper.currentUserId();
        List<String> contentIds = body.get("contentIds");
        readStatusService.batchMarkRead(userId, contentIds);
        return Map.of("success", true);
    }

    /** 清除全部已读状态。 */
    @PostMapping("/read-status/reset")
    public Map<String, Object> resetReadStatus() {
        String userId = SecurityContextHelper.currentUserId();
        readStatusService.reset(userId);
        return Map.of("success", true);
    }
}
