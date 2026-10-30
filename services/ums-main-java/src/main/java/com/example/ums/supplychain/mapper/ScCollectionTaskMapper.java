package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpScCollectionTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 供应链采集任务 Mapper。 */
@Mapper
public interface ScCollectionTaskMapper extends BaseMapper<CpScCollectionTask> {

    /** 幂等 upsert（消息重试时按 task_id 覆盖计数与状态） */
    @Insert("""
            INSERT INTO cp_sc_collection_task
              (task_id, source_name, trigger_type, started_at, finished_at,
               fetched, saved, failed, status)
            VALUES
              (#{t.taskId}, #{t.sourceName}, #{t.triggerType}, #{t.startedAt}, #{t.finishedAt},
               #{t.fetched}, #{t.saved}, #{t.failed}, #{t.status})
            ON DUPLICATE KEY UPDATE
              source_name  = VALUES(source_name),
              trigger_type = VALUES(trigger_type),
              started_at   = VALUES(started_at),
              finished_at  = VALUES(finished_at),
              fetched      = VALUES(fetched),
              saved        = VALUES(saved),
              failed       = VALUES(failed),
              status       = VALUES(status)
            """)
    int upsert(@Param("t") CpScCollectionTask task);
}
