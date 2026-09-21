package com.example.ums.processing.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * cp_news 资讯主表实体（采集侧字段，MyBatis-Plus 映射）。
 *
 * - articleId 为带横线 UUID（CHAR(36)），IdType.INPUT 由业务代码生成
 *   （不用 ASSIGN_UUID：其生成 32 位无横线串，与表结构不符）
 * - sourceName 标记 NEVER：重抓更新时保留首次入库快照
 * - updateById 默认 NOT_NULL 策略：null 字段跳过——与原手写
 *   COALESCE(新值, 旧值) 语义等价（publish_time/author 缺失不清空）
 */
@TableName("cp_news")
public class CpNews {

    @TableId(value = "article_id", type = IdType.INPUT)
    private String articleId;

    private String sourceId;

    @TableField(value = "source_name", updateStrategy = FieldStrategy.NEVER)
    private String sourceName;

    private String title;
    private String originalUrl;
    private String urlHash;
    private LocalDate publishDate;
    private LocalDateTime publishTime;
    private LocalDateTime crawlDate;
    private String author;
    private String language;
    private String taskId;

    /** AI 加工回填（JSON 文本，如 ["BESS","Policy"]）；采集 upsert 时为 null 自动跳过 */
    private String summary;

    private String category;

    /** 内容指纹（去重聚回填）：归一化(标题+正文)的SHA-256 */
    private String contentFingerprint;

    /** 跨源同事件/同内容聚类ID；未归组为NULL */
    private String duplicateGroupId;

    /** 人工审核状态：Pending/NeedsReview/Approved/Rejected */
    private String manualReviewStatus;

    /** 审核备注/驳回原因（V4 新增，管理后台填写） */
    private String reviewComment;

    /** DB 自动维护（DEFAULT/ON UPDATE），仅查询映射用 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }

    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }

    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }

    public LocalDateTime getCrawlDate() { return crawlDate; }
    public void setCrawlDate(LocalDateTime crawlDate) { this.crawlDate = crawlDate; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String contentFingerprint) { this.contentFingerprint = contentFingerprint; }

    public String getDuplicateGroupId() { return duplicateGroupId; }
    public void setDuplicateGroupId(String duplicateGroupId) { this.duplicateGroupId = duplicateGroupId; }

    public String getManualReviewStatus() { return manualReviewStatus; }
    public void setManualReviewStatus(String manualReviewStatus) { this.manualReviewStatus = manualReviewStatus; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
