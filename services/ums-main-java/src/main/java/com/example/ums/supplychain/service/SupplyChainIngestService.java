package com.example.ums.supplychain.service;

import com.example.ums.supplychain.model.GfexWbillMessage;
import com.example.ums.supplychain.model.QuoteMessage;

/** 供应链采集数据加工入库（消费 supplychain.wbill/smm/sci99.fetched）。 */
public interface SupplyChainIngestService {

    /**
     * 加工任务级仓单消息：一致性校验 → 指标序列/数据点 upsert → 异动事件 → 采集任务日志。
     * 全部数据点处理失败时抛异常（消息重试 3 次后进 DLQ）。
     */
    void ingest(GfexWbillMessage message);

    /**
     * 加工 SMM 报价消息（supplychain.smm.fetched）：数值有效性校验 → 指标序列/数据点 upsert
     * → 价格异动事件（PRICE_CHANGE，涨跌幅分档口径同仓单）→ 采集任务日志。
     * 全部数据点处理失败时抛异常（消息重试 3 次后进 DLQ）。
     */
    void ingestSmm(QuoteMessage message);

    /**
     * 加工卓创报价消息（supplychain.sci99.fetched）：同 SMM 口径（区域华东）。
     */
    void ingestSci99(QuoteMessage message);
}
