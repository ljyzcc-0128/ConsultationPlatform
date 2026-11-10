package com.example.ums.supplychain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * supplychain.smm.fetched / supplychain.sci99.fetched 消息体
 * （对齐 ums-crawler supplychain 模块 schema_version 1.0，结构同 GfexWbillMessage 顶层口径）。
 *
 * 任务级消息：一次触发一条，entries 含全部指标点；
 * 全指标失败也发布（entries 可为空），保证采集任务日志（cp_sc_collection_task）落 FAILED。
 * 时间口径：UTC ISO 8601。metric 编码即 series code（Python 侧单一事实源，smm-xx/sci99-xx 前缀）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteMessage(
        String schema_version,
        String event,
        String task_id,
        String fetched_at,
        String source_name,
        String trigger_type,
        String started_at,
        String finished_at,
        Integer fetched,
        Integer failed,
        List<String> errors,
        List<Entry> entries
) {
    /** 一条外部行情/库存/排产指标点 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String metric,          // 指标编码 = series code（smm-cu-spot / sci99-n2-eastchina）
            String metric_name,     // 指标中文名
            String period,          // daily/weekly/monthly
            String data_date,       // 数据日期 yyyy-MM-dd
            String subject,         // 明细主体（同指标下的细分市场，如卓创华东各省市；无细分留空）
            BigDecimal value,       // 指标值
            String unit,            // 单位（元/吨、万吨、吨）
            String region,          // 适用区域（华东等，全国性留空）
            Map<String, Object> raw // 接口原始行（排查归档用，不落库）
    ) {}
}
