package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpChangeEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 指标异动事件 Mapper（feed 包已有 feed.mapper.ChangeEventMapper，此处按指标域命名）。 */
@Mapper
public interface IndicatorChangeEventMapper extends BaseMapper<CpChangeEvent> {

    /** 幂等 upsert（重复采集同一天时覆盖摘要/严重度，status 保持人工处置状态） */
    @Insert("""
            INSERT INTO cp_change_events
              (event_code, object_type, object_id, change_type,
               before_summary, after_summary, detected_at, severity, status, source_name)
            VALUES
              (#{e.eventCode}, #{e.objectType}, #{e.objectId}, #{e.changeType},
               #{e.beforeSummary}, #{e.afterSummary}, #{e.detectedAt},
               #{e.severity}, 'OPEN', #{e.sourceName})
            ON DUPLICATE KEY UPDATE
              before_summary = VALUES(before_summary),
              after_summary  = VALUES(after_summary),
              detected_at    = VALUES(detected_at),
              severity       = VALUES(severity),
              source_name    = VALUES(source_name)
            """)
    int upsert(@Param("e") CpChangeEvent event);
}
