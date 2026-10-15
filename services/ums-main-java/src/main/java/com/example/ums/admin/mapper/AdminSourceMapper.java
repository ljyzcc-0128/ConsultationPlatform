package com.example.ums.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.admin.model.CpSource;
import org.apache.ibatis.annotations.Mapper;

/** 信源配置 Mapper（管理后台）。 */
@Mapper
public interface AdminSourceMapper extends BaseMapper<CpSource> {
}
