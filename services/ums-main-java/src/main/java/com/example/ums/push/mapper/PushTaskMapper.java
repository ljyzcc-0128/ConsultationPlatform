package com.example.ums.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.push.model.PushTask;
import org.apache.ibatis.annotations.Mapper;

/** 推送任务 Mapper。 */
@Mapper
public interface PushTaskMapper extends BaseMapper<PushTask> {
}
