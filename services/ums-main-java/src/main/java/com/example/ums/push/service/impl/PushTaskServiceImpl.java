package com.example.ums.push.service.impl;

import com.example.ums.admin.annotation.Audited;
import com.example.ums.push.dto.PushTaskDto;
import com.example.ums.push.mapper.PushMessageMapper;
import com.example.ums.push.mapper.PushTaskMapper;
import com.example.ums.push.model.PushTask;
import com.example.ums.push.service.PushTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 推送任务管理实现。 */
@Service
public class PushTaskServiceImpl implements PushTaskService {

    private final PushTaskMapper pushTaskMapper;
    private final PushMessageMapper pushMessageMapper;

    public PushTaskServiceImpl(PushTaskMapper pushTaskMapper, PushMessageMapper pushMessageMapper) {
        this.pushTaskMapper = pushTaskMapper;
        this.pushMessageMapper = pushMessageMapper;
    }

    @Override
    public List<PushTaskDto> list() {
        return pushTaskMapper.selectList(null).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Audited(action = "PUSH_TOGGLE", objectType = "push_task")
    public PushTaskDto toggleStatus(String taskId) {
        PushTask task = pushTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推送任务不存在: " + taskId);
        }
        if ("RUNNING".equals(task.getStatus())) {
            task.setStatus("PAUSED");
        } else if ("PAUSED".equals(task.getStatus())) {
            task.setStatus("RUNNING");
        }
        pushTaskMapper.updateById(task);
        return toDto(task);
    }

    private PushTaskDto toDto(PushTask task) {
        long sent = pushMessageMapper.countByTaskId(task.getId());
        long delivered = pushMessageMapper.countDeliveredByTaskId(task.getId());
        long clicked = pushMessageMapper.countClickedByTaskId(task.getId());
        PushTaskDto.Stats stats = new PushTaskDto.Stats(sent, delivered, clicked);
        return new PushTaskDto(
                task.getId(),
                task.getName(),
                task.getTargetDesc(),
                task.getChannel(),
                task.getFrequency(),
                task.getStatus(),
                task.getLastSentAt() != null ? task.getLastSentAt().toString() : null,
                stats,
                List.of()
        );
    }
}
