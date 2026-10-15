package com.example.ums.admin.web;

import com.example.ums.admin.dto.ReviewItemDto;
import com.example.ums.admin.service.AdminReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 审核队列接口（ADM-003：审核通过/驳回、修正分类）。 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    /** 审核队列列表（status 为空查全部：Pending/NeedsReview/Approved/Rejected）。 */
    @GetMapping
    public List<ReviewItemDto> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        return adminReviewService.list(status, limit, offset);
    }

    /** 审核通过。 */
    @PostMapping("/{articleId}/approve")
    public Map<String, Object> approve(@PathVariable String articleId,
                                       @RequestBody(required = false) Map<String, String> body) {
        try {
            adminReviewService.approve(articleId, body == null ? null : body.get("comment"));
            return Map.of("articleId", articleId, "reviewStatus", "Approved");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 审核驳回（必填原因）。 */
    @PostMapping("/{articleId}/reject")
    public Map<String, Object> reject(@PathVariable String articleId,
                                      @RequestBody Map<String, String> body) {
        try {
            adminReviewService.reject(articleId, body.get("reason"));
            return Map.of("articleId", articleId, "reviewStatus", "Rejected");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 修正分类（覆盖原文分类 JSON）。 */
    @PutMapping("/{articleId}/category")
    public Map<String, Object> updateCategory(@PathVariable String articleId,
                                              @RequestBody List<String> categories) {
        try {
            adminReviewService.updateCategory(articleId, categories);
            return Map.of("articleId", articleId, "category", categories);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
