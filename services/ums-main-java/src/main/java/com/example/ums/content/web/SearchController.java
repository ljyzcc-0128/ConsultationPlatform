package com.example.ums.content.web;

import com.example.ums.content.dto.NewsPageDto;
import com.example.ums.content.service.ContentSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全文搜索接口（ngram 全文索引）。
 * 响应与 /api/news 同构（NewsPageDto），实现契约收口。
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final ContentSearchService searchService;

    public SearchController(ContentSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 全文搜索。
     * 示例：GET /api/v1/search?q=储能&limit=20
     * q 为空时返回最新时间线（与 /api/news 行为一致）。
     */
    @GetMapping
    public NewsPageDto search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return searchService.search(q, cursor, limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
