package com.example.ums.subscription.service;

import com.example.ums.subscription.dto.SubscriptionCreateRequest;
import com.example.ums.subscription.dto.SubscriptionDto;

import java.util.List;
import java.util.Map;

/** 用户订阅规则服务。 */
public interface SubscriptionService {

    /** 列出指定用户的全部订阅规则。 */
    List<SubscriptionDto> listByUser(String userId);

    /** 关注对象候选：按订阅类型分组的系统内可选对象（企业/主题/指标/事件等）。 */
    Map<String, List<String>> listOptions();

    /** 为指定用户创建一条订阅规则。 */
    SubscriptionDto create(String userId, SubscriptionCreateRequest req);

    /** 更新指定用户的订阅规则。 */
    SubscriptionDto update(String userId, String id, SubscriptionCreateRequest req);

    /** 删除指定用户的订阅规则。 */
    void delete(String userId, String id);
}
