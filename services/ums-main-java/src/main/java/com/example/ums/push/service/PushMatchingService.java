package com.example.ums.push.service;

/**
 * 推送匹配服务：资讯处理完成后，按订阅规则生成推送消息。
 */
public interface PushMatchingService {

    /** 资讯处理完成回调，根据订阅规则生成推送消息。 */
    void onArticleProcessed(String articleId);
}
