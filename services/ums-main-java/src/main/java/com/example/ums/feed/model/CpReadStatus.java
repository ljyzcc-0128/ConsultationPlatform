package com.example.ums.feed.model;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户已读状态实体（cp_read_status，复合主键 user_id + article_id）。
 */
@TableName("cp_read_status")
public class CpReadStatus {

    private String userId;

    private String articleId;

    private LocalDateTime readAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
