package com.example.ums.processing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.processing.model.CpNewsContent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * cp_news_content 正文表数据访问（与 cp_news 1:1 共享主键）。
 * 正文每次重抓都可能变化，保留一步到位的 upsert（MyBatis-Plus 兼容原生注解 SQL）。
 */
public interface NewsContentMapper extends BaseMapper<CpNewsContent> {

    @Insert("""
            INSERT INTO cp_news_content (news_id, body)
            VALUES (#{newsId}, #{body})
            ON DUPLICATE KEY UPDATE body = VALUES(body)
            """)
    int upsert(@Param("newsId") String newsId, @Param("body") String body);
}
