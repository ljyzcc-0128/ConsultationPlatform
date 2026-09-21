package com.example.ums.admin.web;

import com.example.ums.admin.dto.DlqMessageView;
import com.example.ums.admin.dto.DlqQueueStatus;
import com.example.ums.admin.service.AdminDlqService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 死信队列管理接口（ADM-002/004：异常处理）。 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
public class AdminDlqController {

    private final AdminDlqService adminDlqService;

    public AdminDlqController(AdminDlqService adminDlqService) {
        this.adminDlqService = adminDlqService;
    }

    /** 三个业务死信队列深度。 */
    @GetMapping
    public List<DlqQueueStatus> queues() {
        return adminDlqService.queues();
    }

    /** 预览死信消息（peek 不消费）。 */
    @GetMapping("/{queue}/messages")
    public List<DlqMessageView> messages(@PathVariable String queue,
                                         @RequestParam(defaultValue = "10") int count) {
        try {
            return adminDlqService.messages(queue, count);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 重放：全部死信重新发布回原队列。 */
    @PostMapping("/{queue}/requeue")
    public Map<String, Object> requeue(@PathVariable String queue) {
        try {
            int count = adminDlqService.requeue(queue);
            return Map.of("queue", queue, "requeued", count);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 清空死信队列。 */
    @DeleteMapping("/{queue}")
    public Map<String, Object> purge(@PathVariable String queue) {
        try {
            long count = adminDlqService.purge(queue);
            return Map.of("queue", queue, "purged", count);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
