package com.example.ums.content.web;

import com.example.ums.content.dto.NewsDetailDto;
import com.example.ums.content.dto.NewsPageDto;
import com.example.ums.content.service.NewsQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 内容查询 REST 接口（对前端/小程序）。
 * 契约收口：/api/news 与 /api/v1/contents 同一套方法，分页结构、参数名统一。
 */
@RestController
public class NewsQueryController {

    private final NewsQueryService newsQueryService;

    public NewsQueryController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    /**
     * 资讯时间线（游标分页）。
     * 示例：GET /api/news?limit=5&sourceId=CN-01&category=BESS
     * 契约收口：GET /api/v1/contents 等价同方法。
     */
    @GetMapping(path = {"/api/news", "/api/v1/contents"})
    public NewsPageDto listTimeline(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String reviewStatus) {
        try {
            return newsQueryService.listTimeline(cursor, limit, sourceId, category,
                    language, dateFrom, dateTo, reviewStatus);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 资讯详情（含正文与政策分析）。
     * 契约收口：GET /api/v1/contents/{articleId} 等价同方法。
     */
    @GetMapping(path = {"/api/news/{articleId}", "/api/v1/contents/{articleId}"})
    public ResponseEntity<NewsDetailDto> getDetail(@PathVariable String articleId) {
        NewsDetailDto detail = newsQueryService.getDetail(articleId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }
}
