package com.example.ums.processing.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * cp_news_content 正文表实体（与 cp_news 1:1 共享主键）。
 * created_at / updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP 维护，不映射。
 */
@TableName("cp_news_content")
public class CpNewsContent {

    @TableId(value = "news_id", type = IdType.INPUT)
    private String newsId;

    private String body;

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
