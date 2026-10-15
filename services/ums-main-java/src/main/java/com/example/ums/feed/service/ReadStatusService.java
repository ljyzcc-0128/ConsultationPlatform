package com.example.ums.feed.service;

import java.util.List;

/**
 * 已读状态服务。
 */
public interface ReadStatusService {

    /** 批量标记文章为已读。 */
    void batchMarkRead(String userId, List<String> articleIds);

    /** 清除用户全部已读状态。 */
    void reset(String userId);
}
