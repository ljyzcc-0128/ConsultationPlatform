package com.example.ums.admin.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.mapper.AuditLogMapper;
import com.example.ums.admin.model.CpAuditLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审计日志查询接口（ADM-008，只读；写入由 @Audited 切面自动完成）。 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuditLogMapper auditLogMapper;

    public AdminAuditController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 按操作类型/对象类型过滤，倒序分页。 */
    @GetMapping
    public List<CpAuditLog> list(@RequestParam(required = false) String action,
                                 @RequestParam(required = false) String objectType,
                                 @RequestParam(defaultValue = "50") int limit,
                                 @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditLogMapper.selectList(new LambdaQueryWrapper<CpAuditLog>()
                .eq(action != null && !action.isBlank(), CpAuditLog::getAction, action)
                .eq(objectType != null && !objectType.isBlank(), CpAuditLog::getObjectType, objectType)
                .orderByDesc(CpAuditLog::getLogId)
                .last("LIMIT " + safeLimit + " OFFSET " + Math.max(0, offset)));
    }
}
