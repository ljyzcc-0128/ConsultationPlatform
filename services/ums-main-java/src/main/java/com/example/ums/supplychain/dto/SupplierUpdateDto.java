package com.example.ums.supplychain.dto;

import java.time.LocalDate;

/** 供应商动态。 */
public record SupplierUpdateDto(
        Long id,
        String supplierName,
        String updateType,
        String riskLevel,
        LocalDate date,
        String description) {
}
