package com.example.ums.processing.mapper;

import com.example.ums.processing.model.SourceBrief;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * cp_source 数据访问（入库时校验信源存在 + 取 source_name 快照）。
 */
public interface SourceMapper {

    @Select("SELECT source_id AS sourceId, source_name AS sourceName "
          + "FROM cp_source WHERE source_id = #{sourceId}")
    SourceBrief findById(@Param("sourceId") String sourceId);
}
