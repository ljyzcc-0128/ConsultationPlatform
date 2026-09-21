package com.example.ums.feed.service;

import com.example.ums.feed.dto.FeedItemDto;
import com.example.ums.push.dto.PushRecordDto;

import java.util.List;

/**
 * Feed 服务：我的情报列表 + 推送记录。
 */
public interface FeedService {

    /**
     * 获取 Feed 列表。
     *
     * @param userId 用户ID
     * @param tab    tab 标识：today / major_changes / updates
     */
    List<FeedItemDto> listFeed(String userId, String tab);

    /** 获取用户推送记录列表。 */
    List<PushRecordDto> listPushes(String userId);
}
