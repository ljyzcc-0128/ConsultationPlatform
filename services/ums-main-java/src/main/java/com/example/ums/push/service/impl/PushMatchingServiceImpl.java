package com.example.ums.push.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.push.mapper.PushMessageMapper;
import com.example.ums.push.model.PushMessage;
import com.example.ums.push.service.PushMatchingService;
import com.example.ums.subscription.mapper.SubscriptionMapper;
import com.example.ums.subscription.model.CpSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 推送匹配实现：资讯处理完成后按订阅规则生成站内消息。 */
@Service
public class PushMatchingServiceImpl implements PushMatchingService {

    private static final Logger log = LoggerFactory.getLogger(PushMatchingServiceImpl.class);

    private final SubscriptionMapper subscriptionMapper;
    private final PushMessageMapper pushMessageMapper;

    public PushMatchingServiceImpl(SubscriptionMapper subscriptionMapper,
                                   PushMessageMapper pushMessageMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.pushMessageMapper = pushMessageMapper;
    }

    @Override
    public void onArticleProcessed(String articleId) {
        try {
            // TODO: 接入真实订阅匹配逻辑（按订阅类型/值匹配文章标签、企业、关键词等）
            List<CpSubscription> subscriptions = subscriptionMapper.selectList(
                    new LambdaQueryWrapper<CpSubscription>().eq(CpSubscription::getEnabled, true)
            );
            for (CpSubscription sub : subscriptions) {
                PushMessage message = new PushMessage();
                message.setUserId(sub.getUserId());
                message.setArticleId(articleId);
                message.setChannel("IN_APP");
                message.setTitle("新资讯匹配");
                message.setStatus("SENT");
                pushMessageMapper.insert(message);
            }
        } catch (Exception e) {
            log.warn("推送匹配生成消息失败 articleId={} : {}", articleId, e.getMessage());
        }
    }
}
