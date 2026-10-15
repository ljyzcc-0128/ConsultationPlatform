package com.example.ums.processing.service;

import com.example.ums.processing.model.RawItemFetchedMessage;

/**
 * 采集消息入库服务：消费 raw.item.fetched 后的解析清洗与落库（实现指南 2.1 步骤 3）。
 */
public interface NewsIngestService {

    /**
     * 单条消息入库（幂等：依赖 uk_source_urlhash 唯一约束，重复消费不产生重复记录）。
     *
     * @return article_id（UUID；重抓命中已有记录时返回原 article_id）
     */
    String ingest(RawItemFetchedMessage message);
}
