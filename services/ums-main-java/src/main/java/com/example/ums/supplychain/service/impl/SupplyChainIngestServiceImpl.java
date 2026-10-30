package com.example.ums.supplychain.service.impl;

import com.example.ums.supplychain.mapper.IndicatorChangeEventMapper;
import com.example.ums.supplychain.mapper.IndicatorPointMapper;
import com.example.ums.supplychain.mapper.IndicatorSeriesMapper;
import com.example.ums.supplychain.mapper.ScCollectionTaskMapper;
import com.example.ums.supplychain.model.CpChangeEvent;
import com.example.ums.supplychain.model.CpScCollectionTask;
import com.example.ums.supplychain.model.GfexWbillMessage;
import com.example.ums.supplychain.service.SupplyChainIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 供应链采集加工实现（技术设计文档 3.4/3.5：指标点 + 异动事件 + 采集日志）。
 *
 * 幂等性：series/point/事件/任务日志均按唯一键 upsert，重复消费与跨日重采安全。
 * 失败策略：单品种校验失败计 failed 不中断其他品种；全部数据点处理失败抛异常
 * （本地重试 3 次 → DLQ，可重放）；Python 侧抓取失败（entries 空）仅落 FAILED 日志。
 */
@Service
@RequiredArgsConstructor
public class SupplyChainIngestServiceImpl implements SupplyChainIngestService {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainIngestServiceImpl.class);

    static final String SOURCE_GFEX = "广期所";
    static final String UNIT_LOT = "手";
    static final String DATA_SCOPE = "EXTERNAL_MARKET_DATA";
    static final String METHODOLOGY_NOTE = "广期所仓单日报，统计截止至交易日下午3点";
    static final String CHANGE_TYPE = "WBILL_CHANGE";

    private final IndicatorSeriesMapper seriesMapper;
    private final IndicatorPointMapper pointMapper;
    private final IndicatorChangeEventMapper changeEventMapper;
    private final ScCollectionTaskMapper taskMapper;

    @Override
    public void ingest(GfexWbillMessage message) {
        int saved = 0;
        int failed = message.failed() == null ? 0 : message.failed();
        List<String> errors = new ArrayList<>();

        if (message.entries() != null) {
            for (GfexWbillMessage.Entry entry : message.entries()) {
                try {
                    ingestEntry(message, entry);
                    saved++;
                } catch (Exception e) {
                    failed++;
                    errors.add(entry.variety() + ": " + e.getMessage());
                    log.warn("[supplychain] 品种加工失败 variety={} date={}: {}",
                            entry.variety(), entry.trade_date(), e.getMessage());
                }
            }
        }

        recordCollectionTask(message, saved, failed);
        if (!errors.isEmpty()) {
            log.warn("[supplychain] 部分品种未入库 task_id={} errors={}", message.task_id(), errors);
        }
        // 全部数据点都处理失败 → 抛异常走重试/DLQ；entries 为空（Python 抓取失败）则正常确认
        if (saved == 0 && message.entries() != null && !message.entries().isEmpty()) {
            throw new IllegalStateException("仓单数据全部加工失败 task_id=" + message.task_id()
                    + " errors=" + errors);
        }
    }

    /** 单品种：校验 → series/point upsert → 异动事件 */
    private void ingestEntry(GfexWbillMessage message, GfexWbillMessage.Entry entry) {
        GfexWbillMessage.Metric total = entry.total();
        if (total == null || total.qty() == null) {
            throw new IllegalArgumentException("缺少总计数据");
        }
        int qty = total.qty();
        int lastQty = total.last_qty() == null ? 0 : total.last_qty();
        int diff = total.diff() == null ? 0 : total.diff();
        if (qty != lastQty + diff) {
            throw new IllegalArgumentException(
                    "一致性校验失败: 今日" + qty + " != 昨日" + lastQty + "+增减" + diff);
        }
        LocalDate tradeDate = LocalDate.parse(entry.trade_date());

        String seriesCode = seriesCode(entry.variety());
        String seriesName = entry.variety_name() + "仓单量";
        BigDecimal latestValue = BigDecimal.valueOf(qty);
        BigDecimal momChange = BigDecimal.ZERO;
        if (lastQty > 0) {
            momChange = BigDecimal.valueOf(diff * 100L)
                    .divide(BigDecimal.valueOf(lastQty), 2, RoundingMode.HALF_UP);
        }
        seriesMapper.upsertSeries(seriesCode, seriesName, UNIT_LOT, DATA_SCOPE,
                SOURCE_GFEX, latestValue, momChange, METHODOLOGY_NOTE);
        pointMapper.upsertPoint(seriesCode, tradeDate, "", latestValue);

        if (diff != 0) {
            recordChangeEvent(seriesCode, seriesName, entry, tradeDate, lastQty, qty, diff);
        }
    }

    /** 异动事件：变动幅度分档严重度，摘要带明细归因（前 3 大变动仓库） */
    private void recordChangeEvent(String seriesCode, String seriesName,
                                   GfexWbillMessage.Entry entry, LocalDate tradeDate,
                                   int lastQty, int qty, int diff) {
        double ratio = lastQty > 0 ? Math.abs(diff) * 100.0 / lastQty : 0;
        String severity = ratio >= 10 ? "HIGH" : ratio >= 3 ? "MEDIUM" : "LOW";

        CpChangeEvent event = new CpChangeEvent();
        event.setEventCode(seriesCode + ":" + tradeDate + ":WBILL");
        event.setObjectType("INDICATOR");
        event.setObjectId(seriesCode);
        event.setChangeType(CHANGE_TYPE);
        event.setBeforeSummary(seriesName + " 昨日仓单量 " + lastQty + " " + UNIT_LOT);
        event.setAfterSummary(seriesName + " 今日仓单量 " + qty + " " + UNIT_LOT
                + "，增减 " + diff + " " + UNIT_LOT + "（" + String.format("%.2f%%", ratio) + "）"
                + buildTopMoves(entry.warehouses()));
        event.setDetectedAt(LocalDateTime.now(ZoneOffset.UTC));
        event.setSeverity(severity);
        event.setStatus("OPEN");
        event.setSourceName(SOURCE_GFEX);
        changeEventMapper.upsert(event);
    }

    /** 明细归因摘要："；主要变动：外运龙泉驿 -288手、中物流（常州） -63手" */
    private String buildTopMoves(List<GfexWbillMessage.Row> warehouses) {
        if (warehouses == null || warehouses.isEmpty()) {
            return "";
        }
        String moves = warehouses.stream()
                .filter(w -> w.diff() != null && w.diff() != 0)
                .sorted(Comparator.comparingInt(
                        (GfexWbillMessage.Row w) -> Math.abs(w.diff())).reversed())
                .limit(3)
                .map(w -> w.wh_name() + " " + w.diff() + UNIT_LOT)
                .reduce((a, b) -> a + "、" + b)
                .map(s -> "；主要变动：" + s)
                .orElse("");
        return moves.length() > 200 ? moves.substring(0, 200) : moves;
    }

    /** 采集任务日志（任务级，幂等 upsert） */
    private void recordCollectionTask(GfexWbillMessage message, int saved, int failed) {
        CpScCollectionTask task = new CpScCollectionTask();
        task.setTaskId(message.task_id());
        task.setSourceName(message.source_name() == null ? SOURCE_GFEX : message.source_name());
        task.setTriggerType(message.trigger_type() == null ? "MANUAL" : message.trigger_type());
        task.setStartedAt(parseUtc(message.started_at()));
        task.setFinishedAt(parseUtc(message.finished_at()));
        task.setFetched(message.fetched() == null ? 0 : message.fetched());
        task.setSaved(saved);
        task.setFailed(failed);
        task.setStatus(failed == 0 ? "SUCCESS" : saved > 0 ? "PARTIAL" : "FAILED");
        taskMapper.upsert(task);
    }

    /** 指标编码：品种 → gfex-{variety}-wbill */
    static String seriesCode(String variety) {
        return "gfex-" + variety + "-wbill";
    }

    /** Python UTC ISO 8601（含 Z / +00:00）→ LocalDateTime(UTC)；解析失败回退当前时间 */
    static LocalDateTime parseUtc(String iso) {
        if (iso != null && !iso.isBlank()) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // 落库兜底：时间非法不阻断数据加工
            }
        }
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
