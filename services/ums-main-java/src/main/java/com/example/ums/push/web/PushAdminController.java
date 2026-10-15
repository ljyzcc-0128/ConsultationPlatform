package com.example.ums.push.web;

import com.example.ums.push.dto.PushTaskDto;
import com.example.ums.push.service.PushTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 推送任务管理后台接口。 */
@RestController
@RequestMapping("/api/v1/admin/push")
public class PushAdminController {

    private final PushTaskService pushTaskService;

    public PushAdminController(PushTaskService pushTaskService) {
        this.pushTaskService = pushTaskService;
    }

    /** 推送任务列表（含统计）。 */
    @GetMapping
    public List<PushTaskDto> list() {
        return pushTaskService.list();
    }

    /** 切换推送任务启停状态（RUNNING ↔ PAUSED）。 */
    @PostMapping("/{id}/toggle")
    public PushTaskDto toggle(@PathVariable String id) {
        return pushTaskService.toggleStatus(id);
    }
}
