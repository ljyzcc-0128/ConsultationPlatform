package com.example.ums.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.dto.CollectionTaskDto;
import com.example.ums.admin.mapper.CollectionTaskMapper;
import com.example.ums.admin.model.CpCollectionTask;
import com.example.ums.admin.service.CollectionTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 采集任务记录实现。 */
@Service
public class CollectionTaskServiceImpl implements CollectionTaskService {

    private final CollectionTaskMapper collectionTaskMapper;

    public CollectionTaskServiceImpl(CollectionTaskMapper collectionTaskMapper) {
        this.collectionTaskMapper = collectionTaskMapper;
    }

    @Override
    public List<CollectionTaskDto> list(String sourceId) {
        LambdaQueryWrapper<CpCollectionTask> wrapper = new LambdaQueryWrapper<CpCollectionTask>()
                .orderByDesc(CpCollectionTask::getStartedAt);
        if (sourceId != null && !sourceId.isBlank()) {
            wrapper.eq(CpCollectionTask::getSourceId, sourceId.trim());
        }
        List<CpCollectionTask> tasks = collectionTaskMapper.selectList(wrapper);
        return tasks.stream().map(this::toDto).toList();
    }

    private CollectionTaskDto toDto(CpCollectionTask t) {
        return new CollectionTaskDto(
                t.getTaskId(), t.getSourceId(), t.getSourceName(),
                t.getTriggerType(), t.getStartedAt(), t.getFinishedAt(),
                t.getFetched(), t.getPublished(), t.getFailed(), t.getStatus());
    }
}
