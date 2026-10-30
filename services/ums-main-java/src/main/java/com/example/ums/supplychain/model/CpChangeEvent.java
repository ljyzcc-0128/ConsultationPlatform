package com.example.ums.supplychain.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 指标异动事件（cp_change_events，技术设计文档 3.5）。 */
@TableName("cp_change_events")
public class CpChangeEvent {

    @TableId(value = "event_code", type = IdType.INPUT)
    private String eventCode;

    private String objectType;

    private String objectId;

    private String changeType;

    private String beforeSummary;

    private String afterSummary;

    private LocalDateTime detectedAt;

    private String severity;

    private String status;

    private String sourceName;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getBeforeSummary() { return beforeSummary; }
    public void setBeforeSummary(String beforeSummary) { this.beforeSummary = beforeSummary; }
    public String getAfterSummary() { return afterSummary; }
    public void setAfterSummary(String afterSummary) { this.afterSummary = afterSummary; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
