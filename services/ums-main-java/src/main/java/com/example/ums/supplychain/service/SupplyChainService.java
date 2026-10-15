package com.example.ums.supplychain.service;

import com.example.ums.supplychain.dto.IndicatorSeriesDto;
import com.example.ums.supplychain.dto.InventoryBlockDto;
import com.example.ums.supplychain.dto.SupplierUpdateDto;
import com.example.ums.supplychain.dto.TradeRecordDto;

import java.util.List;

/** 供应链看板服务（SC-001 ~ SC-004）。 */
public interface SupplyChainService {

    /** 原材料市场指标（SC-001）。 */
    List<IndicatorSeriesDto> listMaterials();

    /** 供需与库存（SC-002，组合图）。 */
    InventoryBlockDto getInventory();

    /** 贸易与物流（SC-003，可按状态/关键字筛选）。 */
    List<TradeRecordDto> listTradeLogistics(String status, String query);

    /** 供应商动态（SC-004）。 */
    List<SupplierUpdateDto> listSupplierUpdates();
}
