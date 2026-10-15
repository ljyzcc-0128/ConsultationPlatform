package com.example.ums.admin.web;

import com.example.ums.admin.dto.CollectionTaskDto;
import com.example.ums.admin.service.CollectionTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 采集任务记录接口（ADM-004）。 */
@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {

    private final CollectionTaskService collectionTaskService;

    public AdminTaskController(CollectionTaskService collectionTaskService) {
        this.collectionTaskService = collectionTaskService;
    }

    /** 按信源ID查询采集任务记录（sourceId 为空则返回全部）。 */
    @GetMapping
    public List<CollectionTaskDto> list(@RequestParam(required = false) String sourceId) {
        return collectionTaskService.list(sourceId);
    }
}
