package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpIndicatorPoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
