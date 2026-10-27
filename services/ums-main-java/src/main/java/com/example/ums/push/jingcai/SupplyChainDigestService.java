package com.example.ums.push.jingcai;

import com.example.ums.supplychain.dto.IndicatorSeriesDto;
import com.example.ums.supplychain.dto.InventoryBlockDto;
import com.example.ums.supplychain.service.SupplyChainService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 供应链看板日报卡片内容构建。
 *
 * <p>
 * 从看板同源数据（SupplyChainService）聚合出钉钉 markdown 摘要：
 * 原材料涨跌 → 供需库存最新值 → 贸易物流状态统计。
 * 数据口径与 /v1/supply-chain 接口完全一致，不另建查询。
 */
@Service
public class SupplyChainDigestService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M月d日");

    private final SupplyChainService supplyChainService;

    public SupplyChainDigestService(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    /** 卡片内容 */
    public record DigestCard(String title, String markdown) {
    }

    public DigestCard build() {
        StringBuilder md = new StringBuilder();

        // ---------- 原材料市场 ----------
        List<IndicatorSeriesDto> materials = supplyChainService.listMaterials();
        if (!materials.isEmpty()) {
            md.append("### 原材料市场\n\n");
            for (IndicatorSeriesDto ind : materials) {
                String value = formatValue(ind.latestValue());
                String trend = formatTrend(ind.momChange());
                md.append("- ").append(ind.name()).append("：")
                        .append(value).append(" ").append(ind.unit()).append(" ").append(trend).append('\n');
            }
            md.append('\n');
        }

        // ---------- 供需与库存（每序列取最新数据点） ----------
        InventoryBlockDto inventory = supplyChainService.getInventory();
        if (inventory != null && inventory.series() != null && !inventory.series().isEmpty()) {
            String latestMonth = inventory.months() != null && !inventory.months().isEmpty()
                    ? inventory.months().get(inventory.months().size() - 1)
                    : null;
            md.append("### 供需与库存").append(latestMonth == null ? "\n\n" : "（" + latestMonth + "）\n\n");
            for (InventoryBlockDto.InventorySeriesDto s : inventory.series()) {
                String value = latestValue(s.data());
                if (value != null) {
                    md.append("- ").append(s.name()).append("：").append(value)
                            .append(" ").append(s.unit() == null ? "" : s.unit()).append('\n');
                }
            }
            md.append('\n');
        }

        String date = LocalDate.now().format(DATE_FMT);
        return new DigestCard("供应链日报 · " + date,
                "## 供应链日报 · " + date + "\n\n" + md);
    }

    /** 数值格式化：保留 2 位小数 */
    private String formatValue(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 环比涨跌标记：↑2.3% / ↓1.1% / 持平 */
    private String formatTrend(BigDecimal momChange) {
        if (momChange == null) {
            return "";
        }
        int sign = momChange.signum();
        if (sign == 0) {
            return "（持平）";
        }
        String percent = momChange.abs().stripTrailingZeros().toPlainString() + "%";
        return sign > 0 ? "↑" + percent : "↓" + percent;
    }

    private String latestValue(List<BigDecimal> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        BigDecimal value = data.get(data.size() - 1);
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
