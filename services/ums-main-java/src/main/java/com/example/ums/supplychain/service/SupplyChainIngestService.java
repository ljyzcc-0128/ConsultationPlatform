package com.example.ums.supplychain.service;

import com.example.ums.supplychain.model.GfexWbillMessage;

/** 供应链采集数据加工入库（消费 supplychain.wbill.fetched）。 */
public interface SupplyChainIngestService {

    /**
     * 加工任务级仓单消息：一致性校验 → 指标序列/数据点 upsert → 异动事件 → 采集任务日志。
     * 全部数据点处理失败时抛异常（消息重试 3 次后进 DLQ）。
     */
    void ingest(GfexWbillMessage message);
}
