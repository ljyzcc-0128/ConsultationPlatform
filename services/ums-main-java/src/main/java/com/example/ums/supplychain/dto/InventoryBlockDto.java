package com.example.ums.supplychain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存看板区块（组合图）。
 * months 为共享月份标签，series 为多序列数据。
 */
public record InventoryBlockDto(
        List<String> months,
        List<InventorySeriesDto> series) {

    /** 库存序列项。 */
    public record InventorySeriesDto(
            String name,
            String type,
            String unit,
            String dataScope,
            String sourceName,
            List<BigDecimal> data) {
    }
}
