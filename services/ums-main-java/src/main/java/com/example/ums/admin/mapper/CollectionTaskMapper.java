package com.example.ums.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.admin.model.CpCollectionTask;
import org.apache.ibatis.annotations.Mapper;

/** 采集任务记录 Mapper。 */
@Mapper
public interface CollectionTaskMapper extends BaseMapper<CpCollectionTask> {
}
