package com.example.ums.processing.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * cp_news_analysis 政策与影响分析表（1:1，审核通过/AI 加工后填充，一期暂空）。
 */
@TableName("cp_news_analysis")
public class CpNewsAnalysis {

    @TableId
    private String newsId;

    private String policyStatus;
    private String policyName;
    private LocalDate effectiveDate;
    private String bessRelevance;
    private String jinkoEssRelevance;
    private Integer importanceScore;
    private String aiModel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }
    public String getPolicyStatus() { return policyStatus; }
    public void setPolicyStatus(String policyStatus) { this.policyStatus = policyStatus; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getBessRelevance() { return bessRelevance; }
    public void setBessRelevance(String bessRelevance) { this.bessRelevance = bessRelevance; }
    public String getJinkoEssRelevance() { return jinkoEssRelevance; }
    public void setJinkoEssRelevance(String jinkoEssRelevance) { this.jinkoEssRelevance = jinkoEssRelevance; }
    public Integer getImportanceScore() { return importanceScore; }
    public void setImportanceScore(Integer importanceScore) { this.importanceScore = importanceScore; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
