package com.example.ums.supplychain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.supplychain.dto.IndicatorPointDto;
import com.example.ums.supplychain.dto.IndicatorSeriesDto;
import com.example.ums.supplychain.dto.InventoryBlockDto;
import com.example.ums.supplychain.dto.SupplierUpdateDto;
import com.example.ums.supplychain.dto.TradeRecordDto;
import com.example.ums.supplychain.mapper.IndicatorPointMapper;
import com.example.ums.supplychain.mapper.IndicatorSeriesMapper;
import com.example.ums.supplychain.mapper.InventorySeriesMapper;
import com.example.ums.supplychain.mapper.SupplierUpdateMapper;
import com.example.ums.supplychain.mapper.TradeRecordMapper;
import com.example.ums.supplychain.model.CpIndicatorPoint;
import com.example.ums.supplychain.model.CpIndicatorSeries;
import com.example.ums.supplychain.model.CpInventorySeries;
import com.example.ums.supplychain.model.CpSupplierUpdate;
import com.example.ums.supplychain.model.CpTradeRecord;
import com.example.ums.supplychain.service.SupplyChainService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** 供应链看板实现。 */
@Service
public class SupplyChainServiceImpl implements SupplyChainService {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainServiceImpl.class);

    private final IndicatorSeriesMapper indicatorSeriesMapper;
    private final IndicatorPointMapper indicatorPointMapper;
    private final InventorySeriesMapper inventorySeriesMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final SupplierUpdateMapper supplierUpdateMapper;
    private final ObjectMapper objectMapper;

    public SupplyChainServiceImpl(IndicatorSeriesMapper indicatorSeriesMapper,
                                   IndicatorPointMapper indicatorPointMapper,
                                   InventorySeriesMapper inventorySeriesMapper,
                                   TradeRecordMapper tradeRecordMapper,
                                   SupplierUpdateMapper supplierUpdateMapper,
                                   ObjectMapper objectMapper) {
        this.indicatorSeriesMapper = indicatorSeriesMapper;
        this.indicatorPointMapper = indicatorPointMapper;
        this.inventorySeriesMapper = inventorySeriesMapper;
        this.tradeRecordMapper = tradeRecordMapper;
        this.supplierUpdateMapper = supplierUpdateMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IndicatorSeriesDto> listMaterials() {
        List<CpIndicatorSeries> seriesList = indicatorSeriesMapper.selectList(
                new LambdaQueryWrapper<CpIndicatorSeries>()
                        .orderByAsc(CpIndicatorSeries::getCode));
        return seriesList.stream().map(s -> {
            List<CpIndicatorPoint> points = indicatorPointMapper.selectList(
                    new LambdaQueryWrapper<CpIndicatorPoint>()
                            .eq(CpIndicatorPoint::getSeriesCode, s.getCode())
                            .orderByAsc(CpIndicatorPoint::getPointDate));
            List<IndicatorPointDto> pointDtos = points.stream()
                    .map(p -> new IndicatorPointDto(p.getPointDate(), p.getValue()))
                    .toList();
            return new IndicatorSeriesDto(s.getCode(), s.getName(), s.getUnit(),
                    s.getDataScope(), s.getSourceName(), s.getLatestValue(),
                    s.getMomChange(), pointDtos);
        }).toList();
    }

    @Override
    public InventoryBlockDto getInventory() {
        List<CpInventorySeries> rows = inventorySeriesMapper.selectList(
                new LambdaQueryWrapper<CpInventorySeries>()
                        .orderByAsc(CpInventorySeries::getSortOrder));
        if (rows.isEmpty()) {
            return new InventoryBlockDto(Collections.emptyList(), Collections.emptyList());
        }
        // months 从第一行取（所有行共享同一组月份标签）
        List<String> months = parseStringList(rows.get(0).getMonths());
        List<InventoryBlockDto.InventorySeriesDto> seriesList = rows.stream()
                .map(r -> new InventoryBlockDto.InventorySeriesDto(
                        r.getName(), r.getChartType(), r.getUnit(),
                        r.getDataScope(), r.getSourceName(),
                        parseBigDecimalList(r.getData())))
                .toList();
        return new InventoryBlockDto(months, seriesList);
    }

    @Override
    public List<TradeRecordDto> listTradeLogistics(String status, String query) {
        LambdaQueryWrapper<CpTradeRecord> wrapper = new LambdaQueryWrapper<CpTradeRecord>()
                .orderByDesc(CpTradeRecord::getTradeDate)
                .orderByDesc(CpTradeRecord::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(CpTradeRecord::getStatus, status.trim());
        }
        if (query != null && !query.isBlank()) {
            wrapper.like(CpTradeRecord::getGoods, query.trim());
        }
        List<CpTradeRecord> records = tradeRecordMapper.selectList(wrapper);
        return records.stream().map(this::toTradeRecordDto).toList();
    }

    @Override
    public List<SupplierUpdateDto> listSupplierUpdates() {
        List<CpSupplierUpdate> updates = supplierUpdateMapper.selectList(
                new LambdaQueryWrapper<CpSupplierUpdate>()
                        .orderByDesc(CpSupplierUpdate::getUpdateDate)
                        .orderByDesc(CpSupplierUpdate::getId));
        return updates.stream()
                .map(u -> new SupplierUpdateDto(u.getId(), u.getSupplierName(),
                        u.getUpdateType(), u.getRiskLevel(), u.getUpdateDate(),
                        u.getDescription()))
                .toList();
    }

    private TradeRecordDto toTradeRecordDto(CpTradeRecord r) {
        List<TradeRecordDto.TimelineEntryDto> timeline = parseTimeline(r.getTimeline());
        return new TradeRecordDto(r.getId(), r.getGoods(), r.getCounterparty(),
                r.getRoute(), r.getTradeDate(), r.getStatus(), timeline);
    }

    /** timeline JSON 文本 → List<TimelineEntryDto> */
    private List<TradeRecordDto.TimelineEntryDto> parseTimeline(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(timelineJson,
                    new TypeReference<List<TradeRecordDto.TimelineEntryDto>>() {});
        } catch (Exception e) {
            log.warn("解析 timeline JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** JSON 数组字符串 → List<String> */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析字符串数组 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** JSON 数组字符串 → List<BigDecimal> */
    private List<BigDecimal> parseBigDecimalList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<BigDecimal>>() {});
        } catch (Exception e) {
            log.warn("解析数值数组 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
