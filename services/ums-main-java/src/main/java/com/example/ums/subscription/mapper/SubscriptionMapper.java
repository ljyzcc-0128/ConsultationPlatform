package com.example.ums.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.subscription.model.CpSubscription;
import org.apache.ibatis.annotations.Mapper;

/** 用户订阅规则 Mapper。 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<CpSubscription> {
}
