package com.example.ums.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.subscription.dto.SubscriptionCreateRequest;
import com.example.ums.subscription.dto.SubscriptionDto;
import com.example.ums.subscription.mapper.SubscriptionMapper;
import com.example.ums.subscription.model.CpSubscription;
import com.example.ums.subscription.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** 用户订阅规则服务实现。 */
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    public SubscriptionServiceImpl(SubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
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
