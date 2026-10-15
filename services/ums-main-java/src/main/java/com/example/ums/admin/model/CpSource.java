package com.example.ums.admin.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 信源配置表实体（cp_source，全字段）。
 * 管理后台读写：启停/频率/优先级/备注可改，其余以展示为主。
 */
@TableName("cp_source")
public class CpSource {

    @TableId(value = "source_id", type = IdType.INPUT)
    private String sourceId;

    private String sourceName;

    private String region;

    private String sourceType;

    private String entryUrl;

    private Boolean crawlEnabled;

    private String crawlFrequency;

    private String priority;

    private Boolean pagination;

    private Boolean fetchDetail;

    private Boolean dateFilter;

    private String language;

    /** JSON 数组字符串，如 ["BESS","Policy"] */
    private String category;

    private LocalDateTime lastCrawlTime;

    private String crawlStatus;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getEntryUrl() { return entryUrl; }
    public void setEntryUrl(String entryUrl) { this.entryUrl = entryUrl; }
    public Boolean getCrawlEnabled() { return crawlEnabled; }
    public void setCrawlEnabled(Boolean crawlEnabled) { this.crawlEnabled = crawlEnabled; }
    public String getCrawlFrequency() { return crawlFrequency; }
    public void setCrawlFrequency(String crawlFrequency) { this.crawlFrequency = crawlFrequency; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Boolean getPagination() { return pagination; }
    public void setPagination(Boolean pagination) { this.pagination = pagination; }
    public Boolean getFetchDetail() { return fetchDetail; }
    public void setFetchDetail(Boolean fetchDetail) { this.fetchDetail = fetchDetail; }
    public Boolean getDateFilter() { return dateFilter; }
    public void setDateFilter(Boolean dateFilter) { this.dateFilter = dateFilter; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDateTime getLastCrawlTime() { return lastCrawlTime; }
    public void setLastCrawlTime(LocalDateTime lastCrawlTime) { this.lastCrawlTime = lastCrawlTime; }
    public String getCrawlStatus() { return crawlStatus; }
    public void setCrawlStatus(String crawlStatus) { this.crawlStatus = crawlStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
