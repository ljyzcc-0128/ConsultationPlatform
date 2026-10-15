package com.example.ums.admin.service;

import com.example.ums.admin.dto.ReviewItemDto;

import java.util.List;

/** 内容质量：审核队列（ADM-003：审核通过/驳回、修正分类）。 */
public interface AdminReviewService {

    /** 审核队列列表（status 为空时查全部状态）。 */
    List<ReviewItemDto> list(String status, int limit, int offset);

    /** 审核通过（Pending/NeedsReview → Approved）。 */
    void approve(String articleId, String comment);

    /** 审核驳回（Pending/NeedsReview → Rejected，必填原因）。 */
    void reject(String articleId, String reason);

    /** 修正分类（覆盖 category JSON）。 */
    void updateCategory(String articleId, List<String> categories);
}
