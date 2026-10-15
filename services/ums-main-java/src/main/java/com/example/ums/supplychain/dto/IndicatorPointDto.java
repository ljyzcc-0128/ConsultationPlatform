package com.example.ums.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 指标时序数据点。 */
public record IndicatorPointDto(
        LocalDate date,
        BigDecimal value) {
}
