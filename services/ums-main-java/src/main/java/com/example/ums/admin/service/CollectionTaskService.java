package com.example.ums.admin.service;

import com.example.ums.admin.dto.CollectionTaskDto;

import java.util.List;

/** 采集任务记录服务。 */
public interface CollectionTaskService {

    /** 按信源ID查询采集任务记录（sourceId 为空则返回全部）。 */
    List<CollectionTaskDto> list(String sourceId);
}
