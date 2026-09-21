package com.example.ums.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.user.model.CpUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户表 Mapper（安全模块 LocalAuthProvider 查询用）。 */
@Mapper
public interface UserMapper extends BaseMapper<CpUser> {
}
