package com.example.ums.push.service;

import com.example.ums.push.dto.PushTaskDto;

import java.util.List;

/**
 * 推送任务管理服务。
 */
public interface PushTaskService {

    /** 查询全部推送任务（含统计数据）。 */
    List<PushTaskDto> list();

    /** 切换指定任务的启停状态（RUNNING ↔ PAUSED）。 */
    PushTaskDto toggleStatus(String taskId);
}
