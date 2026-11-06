package com.example.ums.admin.web;

import com.example.ums.admin.dto.XxlJobPage;
import com.example.ums.admin.dto.XxlJobUpsertRequest;
import com.example.ums.admin.service.XxlJobAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * XXL-JOB 定时任务维护接口（ADM-005）：
 * 读取调度中心全部定时任务并支持启停 / 执行一次，
 * 调度中心登录与会话由 XxlJobAdminService 维护。
 */
@RestController
@RequestMapping("/api/v1/admin/xxljob")
@RequiredArgsConstructor
public class XxlJobAdminController {

    private final XxlJobAdminService xxlJobAdminService;

    /** 定时任务分页列表（jobDesc 模糊搜索；triggerStatus -1=全部/0=停止/1=运行）。 */
    @GetMapping("/jobs")
    public XxlJobPage jobs(@RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "20") int length,
            @RequestParam(required = false) String jobDesc,
            @RequestParam(required = false) Integer triggerStatus) {
        return xxlJobAdminService.pageList(start, length, jobDesc, triggerStatus);
    }

    /** 启动任务（恢复调度）。 */
    @PostMapping("/jobs/{id}/start")
    public void start(@PathVariable int id) {
        xxlJobAdminService.start(id);
    }

    /** 停止任务（暂停调度）。 */
    @PostMapping("/jobs/{id}/stop")
    public void stop(@PathVariable int id) {
        xxlJobAdminService.stop(id);
    }

    /** 执行一次（executorParam 可覆盖任务配置参数）。 */
    @PostMapping("/jobs/{id}/trigger")
    public void trigger(@PathVariable int id,
            @RequestParam(required = false) String executorParam) {
        xxlJobAdminService.trigger(id, executorParam);
    }

    /** 新增任务，返回新任务 ID。 */
    @PostMapping("/jobs")
    public Map<String, Object> add(@RequestBody XxlJobUpsertRequest request) {
        try {
            int id = xxlJobAdminService.add(request);
            return Map.of("id", id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 编辑任务。 */
    @PutMapping("/jobs/{id}")
    public void update(@PathVariable int id, @RequestBody XxlJobUpsertRequest request) {
        try {
            xxlJobAdminService.update(id, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 删除任务。 */
    @DeleteMapping("/jobs/{id}")
    public void remove(@PathVariable int id) {
        xxlJobAdminService.remove(id);
    }
}
