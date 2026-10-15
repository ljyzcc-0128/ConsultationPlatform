package com.example.ums.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.admin.model.CpAuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 审计日志 Mapper（写入在切面，查询在 Controller）。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<CpAuditLog> {
}
