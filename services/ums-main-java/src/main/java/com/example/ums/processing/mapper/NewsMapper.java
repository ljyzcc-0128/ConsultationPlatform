package com.example.ums.processing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.processing.model.CpNews;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * cp_news 数据访问（MyBatis-Plus BaseMapper，通用 CRUD）。
 *
 * 幂等 upsert 逻辑见 NewsIngestServiceImpl：
 * - LambdaQueryWrapper 按 (source_id, url_hash) 唯一键查询
 * - insert 走 BaseMapper.insert（IdType.INPUT，业务生成 UUID）
 * - updateById 走 NOT_NULL 策略：null 字段跳过，等价 COALESCE(新值, 旧值)
 */
public interface NewsMapper extends BaseMapper<CpNews> {

    /**
     * 每个信源最新一条资讯（信源视图首屏用）。
     * 排序键与时间线一致：publish_date DESC, article_id DESC；NOT EXISTS 保证每源恰一条。
     */
    @Select("""
            SELECT n.* FROM cp_news n
            WHERE NOT EXISTS (
                SELECT 1 FROM cp_news m
                WHERE m.source_id = n.source_id
                  AND (m.publish_date > n.publish_date
                       OR (m.publish_date = n.publish_date AND m.article_id > n.article_id)))
            ORDER BY n.publish_date IS NULL, n.publish_date DESC, n.article_id DESC
            """)
    List<CpNews> selectLatestPerSource();

    /** 各信源入库总数（信源视图卡片展示用） */
    @Select("SELECT source_id AS sourceId, COUNT(*) AS cnt FROM cp_news GROUP BY source_id")
    List<Map<String, Object>> selectSourceCounts();
}
