package com.example.ums.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.subscription.dto.SubscriptionCreateRequest;
import com.example.ums.subscription.dto.SubscriptionDto;
import com.example.ums.subscription.mapper.SubscriptionMapper;
import com.example.ums.subscription.model.CpSubscription;
import com.example.ums.subscription.service.SubscriptionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 用户订阅规则服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, List<String>> listOptions() {
        // 企业：供应商动态表去重（单组查询失败仅记日志，不影响整体接口）
        Set<String> companies = new LinkedHashSet<>(
                safe(subscriptionMapper::selectCompanyOptions, "企业候选"));
        // 主题/内容域：近期资讯 AI 分类标签（JSON 数组）展开去重
        Set<String> themes = new LinkedHashSet<>();
        for (String raw : safe(subscriptionMapper::selectRecentCategories, "主题候选")) {
            try {
                List<String> tags = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
                themes.addAll(tags);
            } catch (Exception e) {
                log.debug("忽略无法解析的 category: {}", raw);
            }
        }
        themes.removeIf(t -> t == null || t.isBlank());
        // 指标：指标序列名称
        Set<String> indicators = new LinkedHashSet<>(
                safe(subscriptionMapper::selectIndicatorOptions, "指标候选"));
        // 事件：两处事件类型合并
        Set<String> events = new LinkedHashSet<>(
                safe(subscriptionMapper::selectFeedChangeEventTypes, "资讯事件候选"));
        events.addAll(safe(subscriptionMapper::selectCpChangeEventTypes, "指标事件候选"));
        events.removeIf(t -> t == null || t.isBlank());

        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("COMPANY", sorted(companies));
        options.put("THEME", sorted(themes));
        options.put("CONTENT_DOMAIN", sorted(themes));
        options.put("INDICATOR", sorted(indicators));
        options.put("EVENT", sorted(events));
        options.put("KEYWORD", new ArrayList<>());
        return options;
    }

    /** 单组候选查询容错：失败只记 warn 日志并返回空列表，不让整个接口 500。 */
    private List<String> safe(java.util.function.Supplier<List<String>> query, String label) {
        try {
            return query.get();
        } catch (Exception e) {
            log.warn("订阅候选查询失败（{}）：{}", label, e.getMessage());
            return List.of();
        }
    }

    private List<String> sorted(Set<String> values) {
        return values.stream().sorted(Comparator.comparing(String::toLowerCase)).toList();
    }

    @Override
    public List<SubscriptionDto> listByUser(String userId) {
        List<CpSubscription> list = subscriptionMapper.selectList(
                new LambdaQueryWrapper<CpSubscription>()
                        .eq(CpSubscription::getUserId, userId));
        return list.stream().map(this::toDto).toList();
    }

    @Override
    public SubscriptionDto create(String userId, SubscriptionCreateRequest req) {
        CpSubscription entity = new CpSubscription();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setSubscriptionType(req.type());
        entity.setValue(req.value());
        entity.setFrequency(req.frequency());
        entity.setChannel(req.channel());
        entity.setEnabled(req.enabled() != null ? req.enabled() : Boolean.TRUE);
        subscriptionMapper.insert(entity);
        return toDto(entity);
    }

    @Override
    public SubscriptionDto update(String userId, String id, SubscriptionCreateRequest req) {
        CpSubscription entity = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<CpSubscription>()
                        .eq(CpSubscription::getId, id)
                        .eq(CpSubscription::getUserId, userId));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅规则不存在");
        }
        if (req.type() != null) {
            entity.setSubscriptionType(req.type());
        }
        if (req.value() != null) {
            entity.setValue(req.value());
        }
        if (req.frequency() != null) {
            entity.setFrequency(req.frequency());
        }
        if (req.channel() != null) {
            entity.setChannel(req.channel());
        }
        if (req.enabled() != null) {
            entity.setEnabled(req.enabled());
        }
        subscriptionMapper.updateById(entity);
        return toDto(entity);
    }

    @Override
    public void delete(String userId, String id) {
        subscriptionMapper.delete(
                new LambdaQueryWrapper<CpSubscription>()
                        .eq(CpSubscription::getId, id)
                        .eq(CpSubscription::getUserId, userId));
    }

    private SubscriptionDto toDto(CpSubscription entity) {
        return new SubscriptionDto(
                entity.getId(),
                entity.getSubscriptionType(),
                entity.getValue(),
                entity.getFrequency(),
                entity.getChannel(),
                entity.getEnabled());
    }
}
