package com.example.ums.supplychain.service.impl;

import com.example.ums.supplychain.mapper.IndicatorChangeEventMapper;
import com.example.ums.supplychain.mapper.IndicatorPointMapper;
import com.example.ums.supplychain.mapper.IndicatorSeriesMapper;
import com.example.ums.supplychain.mapper.ScCollectionTaskMapper;
import com.example.ums.supplychain.model.CpChangeEvent;
import com.example.ums.supplychain.model.CpIndicatorPoint;
import com.example.ums.supplychain.model.CpScCollectionTask;
import com.example.ums.supplychain.model.GfexWbillMessage;
import com.example.ums.supplychain.model.QuoteMessage;
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
    static final String SOURCE_SMM = "SMM上海有色网";
    static final String SOURCE_SCI99 = "卓创资讯";
    static final String UNIT_LOT = "手";
    static final String DATA_SCOPE = "EXTERNAL_MARKET_DATA";
    static final String METHODOLOGY_NOTE = "广期所仓单日报，统计截止至交易日下午3点";
    static final String METHODOLOGY_SMM = "SMM 上海有色网数据服务（hq.smm.cn），登录账号采集";
    static final String METHODOLOGY_SCI99 = "卓创资讯氮气早间提示（每日更新），华东区域逐市场价格（山东/上海/苏南/苏北/浙江/安徽/江西/福建），上一交易日收盘价";
    static final String CHANGE_TYPE = "WBILL_CHANGE";
    static final String CHANGE_TYPE_PRICE = "PRICE_CHANGE";
    /** 异动分档阈值（%）：仓单与价格类共用一套口径（既有配置） */
    static final double CHANGE_SEVERITY_HIGH_RATIO = 10.0;
    static final double CHANGE_SEVERITY_MEDIUM_RATIO = 3.0;
    /** 报价类指标 series code 前缀（Python 侧单一事实源，Java 防脏数据校验） */
    static final String PREFIX_SMM = "smm-";
    static final String PREFIX_SCI99 = "sci99-";

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
        String severity = severityOf(ratio);

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

    /** 采集任务日志（任务级，幂等 upsert，三类来源共用） */
    private void recordTask(String taskId, String sourceName, String triggerType,
                            String startedAt, String finishedAt,
                            Integer fetched, int saved, int failed) {
        CpScCollectionTask task = new CpScCollectionTask();
        task.setTaskId(taskId);
        task.setSourceName(sourceName == null ? SOURCE_GFEX : sourceName);
        task.setTriggerType(triggerType == null ? "MANUAL" : triggerType);
        task.setStartedAt(parseUtc(startedAt));
        task.setFinishedAt(parseUtc(finishedAt));
        task.setFetched(fetched == null ? 0 : fetched);
        task.setSaved(saved);
        task.setFailed(failed);
        task.setStatus(failed == 0 ? "SUCCESS" : saved > 0 ? "PARTIAL" : "FAILED");
        taskMapper.upsert(task);
    }

    /** 采集任务日志（仓单消息重载） */
    private void recordCollectionTask(GfexWbillMessage message, int saved, int failed) {
        recordTask(message.task_id(), message.source_name(), message.trigger_type(),
                message.started_at(), message.finished_at(), message.fetched(), saved, failed);
    }

    /** 变动幅度（%）→ 严重度分档（仓单/价格共用既有口径：≥10% HIGH、≥3% MEDIUM） */
    static String severityOf(double ratio) {
        return ratio >= CHANGE_SEVERITY_HIGH_RATIO ? "HIGH"
                : ratio >= CHANGE_SEVERITY_MEDIUM_RATIO ? "MEDIUM" : "LOW";
    }

    @Override
    public void ingestSmm(QuoteMessage message) {
        ingestQuote(message, PREFIX_SMM, METHODOLOGY_SMM);
    }

    @Override
    public void ingestSci99(QuoteMessage message) {
        ingestQuote(message, PREFIX_SCI99, METHODOLOGY_SCI99);
    }

    /**
     * SMM/卓创共用加工：数值有效性校验 → series（含 region）/point upsert → 价格异动事件
     * → 采集任务日志。失败策略与仓单一致：单条失败计 failed 不中断，全部失败抛异常进 DLQ。
     */
    private void ingestQuote(QuoteMessage message, String seriesPrefix, String methodology) {
        int saved = 0;
        int failed = message.failed() == null ? 0 : message.failed();
        List<String> errors = new ArrayList<>();

        if (message.entries() != null) {
            for (QuoteMessage.Entry entry : message.entries()) {
                try {
                    ingestQuoteEntry(message, entry, seriesPrefix, methodology);
                    saved++;
                } catch (Exception e) {
                    failed++;
                    errors.add(entry.metric() + ": " + e.getMessage());
                    log.warn("[supplychain] 报价指标加工失败 metric={} date={}: {}",
                            entry.metric(), entry.data_date(), e.getMessage());
                }
            }
        }

        recordTask(message.task_id(), message.source_name(), message.trigger_type(),
                message.started_at(), message.finished_at(), message.fetched(), saved, failed);
        if (!errors.isEmpty()) {
            log.warn("[supplychain] 部分报价指标未入库 task_id={} errors={}", message.task_id(), errors);
        }
        // 全部数据点都处理失败 → 抛异常走重试/DLQ；entries 为空（Python 抓取/登录失败）则正常确认
        if (saved == 0 && message.entries() != null && !message.entries().isEmpty()) {
            throw new IllegalStateException("报价数据全部加工失败 task_id=" + message.task_id()
                    + " errors=" + errors);
        }
    }

    /** 单条报价：校验 → series/point upsert → 与上一数据点比对生成价格异动事件 */
    private void ingestQuoteEntry(QuoteMessage message, QuoteMessage.Entry entry,
                                  String seriesPrefix, String methodology) {
        if (entry.metric() == null || !entry.metric().startsWith(seriesPrefix)) {
            throw new IllegalArgumentException("指标编码非法（需以 " + seriesPrefix + " 开头）: "
                    + entry.metric());
        }
        if (entry.value() == null || entry.value().signum() < 0) {
            throw new IllegalArgumentException("指标值缺失或为负: " + entry.value());
        }
        LocalDate dataDate;
        try {
            dataDate = LocalDate.parse(entry.data_date());
        } catch (Exception e) {
            throw new IllegalArgumentException("数据日期非法: " + entry.data_date());
        }

        String seriesCode = entry.metric();
        String seriesName = entry.metric_name() == null || entry.metric_name().isBlank()
                ? seriesCode : entry.metric_name();
        String unit = entry.unit() == null || entry.unit().isBlank() ? "元/吨" : entry.unit();
        String region = entry.region() == null ? "" : entry.region();
        // 明细主体：同指标下的细分市场（卓创华东各省市行），无细分留空
        String subject = entry.subject() == null ? "" : entry.subject().trim();
        BigDecimal value = entry.value();

        // 环比：与严格早于本日的最近数据点比对（按主体各自成序列；周/月度序列自动跨周期）
        BigDecimal momChange = BigDecimal.ZERO;
        CpIndicatorPoint prev = pointMapper.selectPrevPoint(seriesCode, dataDate, subject);
        if (prev != null && prev.getValue() != null
                && prev.getValue().compareTo(BigDecimal.ZERO) != 0) {
            momChange = value.subtract(prev.getValue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prev.getValue().abs(), 2, RoundingMode.HALF_UP);
        }

        String note = region.isBlank() ? methodology
                : methodology + "；适用区域：" + region;
        if (entry.period() != null && !entry.period().isBlank()) {
            note = note + "；周期：" + entry.period();
        }
        seriesMapper.upsertSeriesWithRegion(seriesCode, seriesName, unit, DATA_SCOPE,
                message.source_name(), value, momChange, note, region);
        pointMapper.upsertPoint(seriesCode, dataDate, subject, value);

        // 价格异动事件（复用仓单涨跌幅分档阈值；事件编码含主体，多市场互不覆盖）
        if (prev != null && prev.getValue() != null) {
            recordPriceChangeEvent(seriesCode, seriesName, unit, dataDate,
                    prev.getValue(), value, momChange, region, subject, message.source_name());
        }
    }

    /** 价格异动事件：摘要含前值/现值与涨跌幅（event_code 幂等：指标:日期:PRICE[:主体]） */
    private void recordPriceChangeEvent(String seriesCode, String seriesName, String unit,
                                        LocalDate dataDate, BigDecimal prevValue, BigDecimal value,
                                        BigDecimal momChange, String region, String subject,
                                        String sourceName) {
        double ratio = prevValue.abs().compareTo(BigDecimal.ZERO) == 0
                ? 0 : Math.abs(momChange.doubleValue());
        String label = (region.isBlank() ? "" : "（" + region + (subject.isBlank() ? "" : "·" + subject) + "）")
                .replace("（）", "");
        CpChangeEvent event = new CpChangeEvent();
        event.setEventCode(seriesCode + ":" + dataDate + ":PRICE"
                + (subject.isBlank() ? "" : ":" + subject));
        event.setObjectType("INDICATOR");
        event.setObjectId(seriesCode);
        event.setChangeType(CHANGE_TYPE_PRICE);
        event.setBeforeSummary(seriesName + label
                + " 上一期 " + prevValue.stripTrailingZeros().toPlainString() + " " + unit);
        event.setAfterSummary(seriesName + label
                + " 本期 " + value.stripTrailingZeros().toPlainString() + " " + unit
                + "，环比 " + momChange.stripTrailingZeros().toPlainString()
                + "%（" + String.format("%.2f%%", ratio) + " 幅度分档）");
        event.setDetectedAt(LocalDateTime.now(ZoneOffset.UTC));
        event.setSeverity(severityOf(ratio));
        event.setStatus("OPEN");
        event.setSourceName(sourceName);
        changeEventMapper.upsert(event);
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
