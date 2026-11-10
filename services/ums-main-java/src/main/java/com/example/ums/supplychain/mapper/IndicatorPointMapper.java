package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpIndicatorPoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.ibatis.annotations.Select;

/** 指标数据点 Mapper。 */
@Mapper
public interface IndicatorPointMapper extends BaseMapper<CpIndicatorPoint> {

    /** 幂等 upsert：同 (序列, 日期, 主体) 重复采集时覆盖值 */
    @Insert("""
            INSERT INTO cp_indicator_point (series_code, point_date, subject, value)
            VALUES (#{seriesCode}, #{pointDate}, #{subject}, #{value})
            ON DUPLICATE KEY UPDATE value = VALUES(value)
            """)
    int upsertPoint(@Param("seriesCode") String seriesCode,
                    @Param("pointDate") LocalDate pointDate,
                    @Param("subject") String subject,
                    @Param("value") BigDecimal value);

    /** 严格早于 pointDate 的最近数据点（价格类指标环比/异动检测基期） */
    @Select("""
            SELECT * FROM cp_indicator_point
            WHERE series_code = #{seriesCode} AND subject = #{subject} AND point_date < #{pointDate}
            ORDER BY point_date DESC LIMIT 1
            """)
    CpIndicatorPoint selectPrevPoint(@Param("seriesCode") String seriesCode,
                                     @Param("pointDate") LocalDate pointDate,
                                     @Param("subject") String subject);
}
