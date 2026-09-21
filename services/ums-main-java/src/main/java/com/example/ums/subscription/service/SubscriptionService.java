package com.example.ums.subscription.service;

import com.example.ums.subscription.dto.SubscriptionCreateRequest;
import com.example.ums.subscription.dto.SubscriptionDto;

import java.util.List;

/** 用户订阅规则服务。 */
public interface SubscriptionService {

    /** 列出指定用户的全部订阅规则。 */
    List<SubscriptionDto> listByUser(String userId);

    /** 为指定用户创建一条订阅规则。 */
    SubscriptionDto create(String userId, SubscriptionCreateRequest req);

    /** 更新指定用户的订阅规则。 */
    SubscriptionDto update(String userId, String id, SubscriptionCreateRequest req);

    /** 删除指定用户的订阅规则。 */
    void delete(String userId, String id);
}
