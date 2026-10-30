package com.example.ums.supplychain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * supplychain.wbill.fetched 消息体（对齐 ums-crawler supplychain 模块 schema_version 1.0）。
 *
 * 任务级消息：一次触发一条，entries 含全部品种（lc/si/ps）数据；
 * 全品种失败也发布（entries 可为空），保证采集任务日志（cp_sc_collection_task）落 FAILED。
 * 时间口径：UTC ISO 8601。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GfexWbillMessage(
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
    /** 一个品种一个交易日的仓单数据 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String variety,
            String variety_name,
            String trade_date,
            Metric total,
            List<Row> warehouses
    ) {}

    /** 总量口径：今日/昨日仓单量与增减（手） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(Integer last_qty, Integer qty, Integer diff) {}

    /** 仓库/商标明细行 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(String wh_name, String trademark,
                      Integer last_qty, Integer qty, Integer diff) {}
}
