package com.example.ums.subscription.web;

import com.example.ums.security.SecurityContextHelper;
import com.example.ums.subscription.dto.SubscriptionCreateRequest;
import com.example.ums.subscription.dto.SubscriptionDto;
import com.example.ums.subscription.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 当前用户订阅规则 REST 接口。 */
@RestController
@RequestMapping("/api/me/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** 当前用户订阅列表。 */
    @GetMapping
    public List<SubscriptionDto> list() {
        return subscriptionService.listByUser(currentUserId());
    }

    /** 创建订阅规则。 */
    @PostMapping
    public ResponseEntity<SubscriptionDto> create(@RequestBody SubscriptionCreateRequest req) {
        SubscriptionDto dto = subscriptionService.create(currentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** 更新订阅规则。 */
    @PutMapping("/{id}")
    public SubscriptionDto update(@PathVariable String id, @RequestBody SubscriptionCreateRequest req) {
        return subscriptionService.update(currentUserId(), id, req);
    }

    /** 删除订阅规则。 */
    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable String id) {
        subscriptionService.delete(currentUserId(), id);
        return Map.of("success", true);
    }

    private String currentUserId() {
        String userId = SecurityContextHelper.currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
