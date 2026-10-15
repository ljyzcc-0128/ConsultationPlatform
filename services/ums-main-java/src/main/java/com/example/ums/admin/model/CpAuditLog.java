package com.example.ums.admin.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 管理操作审计日志实体（cp_audit_log）。
 * 写入由 AuditLogAspect 自动完成；本实体供查询展示。
 */
@TableName("cp_audit_log")
public class CpAuditLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private String operator;

    private String action;

    private String objectType;

    private String objectId;

    /** JSON 字符串，接口层原样返回 */
    private String detail;

    private LocalDateTime createdAt;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
