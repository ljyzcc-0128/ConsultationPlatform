package com.example.ums.push.web;

import com.example.ums.push.dto.PushTaskDto;
import com.example.ums.push.jingcai.JingcaiCardSender;
import com.example.ums.push.jingcai.JingcaiReceiverResolver;
import com.example.ums.push.jingcai.SupplyChainDigestService;
import com.example.ums.push.service.PushTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/** 推送任务管理后台接口。 */
@RestController
@RequestMapping("/api/v1/admin/push")
@RequiredArgsConstructor
public class PushAdminController {

    private final PushTaskService pushTaskService;
    private final SupplyChainDigestService digestService;
    private final JingcaiCardSender jingcaiCardSender;
    private final JingcaiReceiverResolver receiverResolver;

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

    /**
     * 手动触发晶彩卡片推送：发送当前供应链看板快照日报。
     * 用于联调验证与定时任务外的按需推送。
     */
    @PostMapping("/jingcai/digest")
    public Map<String, Object> sendJingcaiDigest() {
        try {
            SupplyChainDigestService.DigestCard card = digestService.build();
            long taskId = jingcaiCardSender.send(card.title(), card.markdown());
            return Map.of(
                    "title", card.title(),
                    "taskId", taskId,
                    "receivers", receiverResolver.resolve());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
