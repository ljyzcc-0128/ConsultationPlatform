package com.example.ums.feed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.feed.model.ChangeEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 变化事件 Mapper（change_events）。
 */
@Mapper
public interface ChangeEventMapper extends BaseMapper<ChangeEvent> {
}
