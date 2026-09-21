package com.example.ums.supplychain.web;

import com.example.ums.supplychain.dto.IndicatorSeriesDto;
import com.example.ums.supplychain.dto.InventoryBlockDto;
import com.example.ums.supplychain.dto.SupplierUpdateDto;
import com.example.ums.supplychain.dto.TradeRecordDto;
import com.example.ums.supplychain.service.SupplyChainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供应链看板接口（SC-001 ~ SC-004）。
 * 需登录认证（SecurityConfig: /api/v1/supply-chain/** authenticated）。
 */
@RestController
@RequestMapping("/api/v1/supply-chain")
public class SupplyChainController {

    private final SupplyChainService supplyChainService;

    public SupplyChainController(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    /** SC-001：原材料市场指标。 */
    @GetMapping("/materials")
    public Map<String, Object> materials() {
        List<IndicatorSeriesDto> indicators = supplyChainService.listMaterials();
        return Map.of("indicators", indicators);
    }

    /** SC-002：供需与库存（组合图）。 */
    @GetMapping("/inventory")
    public InventoryBlockDto inventory() {
        return supplyChainService.getInventory();
    }

    /** SC-003：贸易与物流（可按状态/关键字筛选）。 */
    @GetMapping("/trade-logistics")
    public Map<String, Object> tradeLogistics(
            @RequestParam(required = false) String status,
            @RequestParam(name = "q", required = false) String q) {
        List<TradeRecordDto> records = supplyChainService.listTradeLogistics(status, q);
        return Map.of("records", records);
    }

    /** SC-004：供应商动态。 */
    @GetMapping("/suppliers")
    public Map<String, Object> suppliers() {
        List<SupplierUpdateDto> updates = supplyChainService.listSupplierUpdates();
        return Map.of("updates", updates);
    }
}
