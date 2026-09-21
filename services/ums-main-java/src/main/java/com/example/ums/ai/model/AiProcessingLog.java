package com.example.ums.ai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * ai_processing_log 实体（AI-003 可追溯）。
 * created_at 由数据库维护；id 自增。
 */
@TableName("ai_processing_log")
public class AiProcessingLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String newsId;

    private String operation;

    private String provider;

    private String model;

    private String status;

    private Integer latencyMs;

    private String inputDigest;

    private String errorSummary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNewsId() {
        return newsId;
    }

    public void setNewsId(String newsId) {
        this.newsId = newsId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getInputDigest() {
        return inputDigest;
    }

    public void setInputDigest(String inputDigest) {
        this.inputDigest = inputDigest;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }
}
