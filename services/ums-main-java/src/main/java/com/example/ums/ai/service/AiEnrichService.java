package com.example.ums.ai.service;

/**
 * AI 加工编排：读正文 → 摘要/分类 → 回写 cp_news → 发 content.processed。
 */
public interface AiEnrichService {

    /**
     * 加工单条资讯。
     * SKIPPED（AI 未配置/正文为空）正常返回；加工失败抛异常走消费重试 → DLQ。
     */
    void enrich(String articleId);
}
