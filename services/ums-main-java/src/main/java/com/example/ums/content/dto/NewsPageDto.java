package com.example.ums.content.dto;

import java.util.List;

/**
 * 时间线分页响应（keyset 游标，无总数字段——大列表 count 代价高，前端用 hasMore 判断）。
 */
public record NewsPageDto(
        List<NewsListItemDto> items,
        String nextCursor,
        boolean hasMore) {
}
