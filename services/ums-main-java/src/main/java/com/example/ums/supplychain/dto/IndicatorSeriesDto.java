package com.example.ums.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 指标序列（含时序点）。 */
public record IndicatorSeriesDto(
        String code,
        String name,
        String unit,
        String dataScope,
        String sourceName,
        BigDecimal latestValue,
        BigDecimal momChange,
        List<IndicatorPointDto> points,
        /** 明细主体最新值（同指标多市场，如卓创华东各省市）；无细分指标为空列表 */
        List<SubjectPoint> subjects,
        /** 多市场按日趋势（最新 3 个交易日，日期升序）：前端绘制「横轴市场、多日曲线对比」图 */
        List<SubjectDayDto> subjectTrend) {

    /** 一条主体明细：市场名 + 最新价 + 对应日期 */
    public record SubjectPoint(String subject, BigDecimal value, LocalDate date) {
    }

    /** 一个交易日下各市场的价格快照 */
    public record SubjectDayDto(LocalDate date, List<SubjectPoint> points) {
    }
}
