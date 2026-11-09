package com.example.ums.push.jingcai;

import com.example.ums.llm.LlmAdapter;
import com.example.ums.llm.LlmOptions;
import com.example.ums.llm.LlmProperties;
import com.example.ums.supplychain.dto.IndicatorSeriesDto;
import com.example.ums.supplychain.dto.InventoryBlockDto;
import com.example.ums.supplychain.service.SupplyChainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class SupplyChainDigestService {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainDigestService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M月d日");

    private final SupplyChainService supplyChainService;
    private final LlmAdapter llmAdapter;
    private final LlmProperties llmProperties;
    private final JingcaiProperties jingcaiProperties;

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

        // ---------- AI 综述（LLM 未配置/失败时自动降级为纯数据版） ----------
        String overview = buildAiOverview(md.toString().trim());
        if (overview != null) {
            md.insert(0, "### 今日综述\n\n" + overview + "\n\n");
        }

        String date = LocalDate.now().format(DATE_FMT);
        return new DigestCard("供应链日报 · " + date,
                "## 供应链日报 · " + date + "\n\n" + md);
    }

    /**
     * 调用 LLM 生成 2-3 句数据综述。
     * 开关关闭、LLM 未配置或调用失败时返回 null（日报仍可发出，只是没有综述段）。
     */
    private String buildAiOverview(String statsMarkdown) {
        if (!jingcaiProperties.isAiSummary() || !llmProperties.isConfigured()) {
            return null;
        }
        try {
            String overview = llmAdapter.chat(
                    "你是供应链资讯编辑，负责撰写每日日报的数据综述。要求："
                            + "1) 只依据给定数据撰写，不得编造或引入外部信息；"
                            + "2) 2-3 句简明中文，点出最显著的变化与值得注意的风险；"
                            + "3) 不使用 markdown 标题与列表，全文不超过 120 字。",
                    statsMarkdown,
                    LlmOptions.builder().temperature(0.3).maxTokens(1024).build());
            return overview == null || overview.isBlank() ? null : overview.trim();
        } catch (Exception e) {
            log.warn("AI 综述生成失败，日报降级为纯数据版: {}", e.getMessage());
            return null;
        }
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
