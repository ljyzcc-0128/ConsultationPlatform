package com.example.ums.supplychain.dto;

import java.time.LocalDate;
import java.util.List;

/** 贸易物流记录。 */
public record TradeRecordDto(
        String id,
        String goods,
        String counterparty,
        String route,
        LocalDate date,
        String status,
        List<TimelineEntryDto> timeline) {

    /** 时间线条目。 */
    public record TimelineEntryDto(String time, String event) {
    }
}
