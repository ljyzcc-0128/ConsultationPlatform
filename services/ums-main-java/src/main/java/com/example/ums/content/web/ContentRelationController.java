package com.example.ums.content.web;

import com.example.ums.content.dto.RelatedContentDto;
import com.example.ums.content.dto.TimelineEventDto;
import com.example.ums.content.service.ContentSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内容关联接口（相关内容 + 事件时间线）。
 */
@RestController
@RequestMapping("/api/v1/contents/{id}")
public class ContentRelationController {

    private final ContentSearchService searchService;

    public ContentRelationController(ContentSearchService searchService) {
        this.searchService = searchService;
    }

    /** 相关内容（同 duplicate_group_id 优先，不足按同 category 补充）。 */
    @GetMapping("/related")
    public Map<String, Object> related(@PathVariable String id) {
        List<RelatedContentDto> items = searchService.findRelated(id);
        return Map.of("items", items);
    }

    /** 事件时间线（查 change_events 表）。 */
    @GetMapping("/timeline")
    public Map<String, Object> timeline(@PathVariable String id) {
        List<TimelineEventDto> events = searchService.findTimeline(id);
        return Map.of("events", events);
    }
}
