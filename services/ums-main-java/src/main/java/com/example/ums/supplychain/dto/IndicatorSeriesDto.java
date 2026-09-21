package com.example.ums.supplychain.dto;

import java.math.BigDecimal;
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
        List<IndicatorPointDto> points) {
}
