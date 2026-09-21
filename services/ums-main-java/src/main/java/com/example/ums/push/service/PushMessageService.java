package com.example.ums.push.service;

import com.example.ums.push.dto.PushRecordDto;

import java.util.List;

/**
 * 推送消息服务。
 */
public interface PushMessageService {

    /** 查询指定用户的推送记录列表。 */
    List<PushRecordDto> listByUser(String userId);
}
