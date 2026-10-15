package com.example.ums.content.service;

import com.example.ums.content.dto.NewsDetailDto;
import com.example.ums.content.dto.NewsPageDto;

import java.time.LocalDate;

/**
 * 内容查询服务（对外 REST 的业务层）。
 */
public interface NewsQueryService {

    /**
     * 时间线（publish_date DESC, article_id DESC；publish_date 为 NULL 的排末尾）。
     *
     * @param cursor   上一页返回的 nextCursor；null 表示第一页
     * @param limit    页大小（1~50，默认 20）
     * @param sourceId 信源筛选（可空）
     * @param category 主题标签筛选（可空，匹配 JSON 数组元素，如 "BESS"）
     * @param language 语言筛选（可空，BCP 47）
     * @param dateFrom 发布日期起（含，可空）
     * @param dateTo   发布日期止（含，可空）
     * @param reviewStatus 审核状态筛选（可空；一期无审核流程默认不筛）
     */
    NewsPageDto listTimeline(String cursor, int limit, String sourceId, String category,
                             String language, LocalDate dateFrom, LocalDate dateTo,
                             String reviewStatus);

    /** 详情（主表 + 正文 + 政策分析）；不存在返回 null */
    NewsDetailDto getDetail(String articleId);
}
