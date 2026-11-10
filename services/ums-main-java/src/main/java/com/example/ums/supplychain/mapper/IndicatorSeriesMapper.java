package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpIndicatorSeries;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 指标序列 Mapper。 */
@Mapper
public interface IndicatorSeriesMapper extends BaseMapper<CpIndicatorSeries> {

    /**
     * 采集落库 upsert：序列不存在则创建（自动注册），存在则刷新最新值与环比。
     * 注：P0 每日一次顺序采集，值按消息顺序覆盖即可；若未来并发乱序需加日期守卫。
     */
    @Insert("""
            INSERT INTO cp_indicator_series
              (code, name, unit, data_scope, source_name, latest_value, mom_change, methodology_note)
            VALUES
              (#{code}, #{name}, #{unit}, #{dataScope}, #{sourceName}, #{latestValue}, #{momChange}, #{methodologyNote})
            ON DUPLICATE KEY UPDATE
              name             = VALUES(name),
              latest_value     = VALUES(latest_value),
              mom_change       = VALUES(mom_change),
              methodology_note = VALUES(methodology_note)
            """)
    int upsertSeries(@Param("code") String code,
                     @Param("name") String name,
                     @Param("unit") String unit,
                     @Param("dataScope") String dataScope,
                     @Param("sourceName") String sourceName,
                     @Param("latestValue") java.math.BigDecimal latestValue,
                     @Param("momChange") java.math.BigDecimal momChange,
                     @Param("methodologyNote") String methodologyNote);

    /** 带 region 的 upsert（区域价格类指标用，SMM/卓创区域口径；V8 已建 region 列） */
    @Insert("""
            INSERT INTO cp_indicator_series
              (code, name, unit, data_scope, source_name, latest_value, mom_change, methodology_note, region)
            VALUES
              (#{code}, #{name}, #{unit}, #{dataScope}, #{sourceName}, #{latestValue}, #{momChange}, #{methodologyNote}, #{region})
            ON DUPLICATE KEY UPDATE
              name             = VALUES(name),
              latest_value     = VALUES(latest_value),
              mom_change       = VALUES(mom_change),
              methodology_note = VALUES(methodology_note),
              region           = VALUES(region)
            """)
    int upsertSeriesWithRegion(@Param("code") String code,
                               @Param("name") String name,
                               @Param("unit") String unit,
                               @Param("dataScope") String dataScope,
                               @Param("sourceName") String sourceName,
                               @Param("latestValue") java.math.BigDecimal latestValue,
                               @Param("momChange") java.math.BigDecimal momChange,
                               @Param("methodologyNote") String methodologyNote,
                               @Param("region") String region);
}
